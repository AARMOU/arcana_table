/** Transport-independent, bounded guest. No sockets, node imports, secrets or model calls. */
import { selectAction, chooseBottomCards } from './game-core.mjs';
const clone = v => structuredClone(v);
const ids = v => Array.isArray(v) && v.every(x => typeof x === 'string' && x.length > 0) ? [...new Set(v)] : [];
const simple = info => info?.isAffordable !== false && !info?.additionalCostInfo && !info?.hasXCost &&
  !info?.requiresChoices && !info?.requiresManaColorChoice && !info?.crewInfo;

/** Candidates are client-side IDs, NOT engine action IDs. Host validates every action. */
export function candidates(packet, playerId) {
  if (packet?.type !== 'stateUpdate' || packet.interactive !== true) return [];
  const d = packet.pendingDecision;
  const out = [];
  const add = action => { if (out.length < 32 && action?.playerId === playerId) out.push({ id: String(out.length), action: clone(action) }); };
  const response = r => add({ type: 'SubmitDecision', playerId, response: { ...r, decisionId: d.id } });
  if (d && d.type !== 'PlayCardDecision') {
    if (!d.id || d.playerId !== playerId) return [];
    if (d.type === 'YesNoDecision') for (const choice of [false, true]) response({ type: 'YesNoResponse', choice });
    if (d.type === 'ChooseOptionDecision' && Array.isArray(d.options) && d.options.length <= 32)
      d.options.forEach((_, optionIndex) => response({ type: 'OptionChosenResponse', optionIndex }));
    if (d.type === 'ChooseNumberDecision' && Number.isSafeInteger(d.minValue) && Number.isSafeInteger(d.maxValue) && d.minValue <= d.maxValue)
      for (const number of [...new Set([d.minValue, d.maxValue])]) response({ type: 'NumberChosenResponse', number });
    // Argentum's typed card selection. Only host-offered IDs, respecting exact
    // min/max counts; custom cross-card constraints must never be guessed.
    if (d.type === 'SelectCardsDecision') {
      const options = d.options;
      const min = d.minSelections, max = d.maxSelections;
      const constrained = d.onePerCardType === true || d.onePerColor === true ||
        d.onePerCardName === true || d.onePerBasicLandType === true ||
        d.onePerPower === true || d.selectionLimits != null ||
        d.maxTotalManaValue != null || d.minTotalManaValue != null ||
        d.maxTotalPower != null ||
        (Array.isArray(d.conditionalMinimums) && d.conditionalMinimums.length > 0);
      const validOptions = Array.isArray(options) && options.length <= 4096 &&
        options.every(id => typeof id === 'string' && id.length > 0 && id.length <= 200) &&
        new Set(options).size === options.length;
      if (validOptions && Number.isSafeInteger(min) && Number.isSafeInteger(max) &&
          min >= 0 && max >= min && min <= 32 &&
          (!Array.isArray(d.nonSelectableOptions) ||
            !options.some(id => d.nonSelectableOptions.includes(id)))) {
        if (!constrained) {
          // A zero-selection is a legal decline when minimum is zero.
          if (min === 0) response({type:'CardsSelectedResponse',selectedCards:[]});
          // For simple choices every offered ID is independently selectable.
          // Bound the model's decision space; never truncate a *required* selection.
          if (min > 0 && options.length >= min) {
            const slots = min === 1 ? Math.min(options.length,24) : Math.min(options.length-min+1,8);
            for (let offset=0; offset<slots; offset++)
              response({type:'CardsSelectedResponse',selectedCards:options.slice(offset,offset+min)});
          } else if (min === 0 && max > 0) {
            for (const id of options.slice(0,Math.min(options.length,24)))
              response({type:'CardsSelectedResponse',selectedCards:[id]});
          }
        } else if (min === 0 && d.minTotalManaValue == null &&
                   (!Array.isArray(d.conditionalMinimums) || d.conditionalMinimums.length === 0)) {
          // Optional constrained selection can safely decline, but never fake
          // category/colour/power/counter allocations from incomplete metadata.
          response({type:'CardsSelectedResponse',selectedCards:[]});
        }
      }
    }
    // Single-slot/single-pick targets only; coupled/aggregate constraints must pause.
    if (d.type === 'ChooseTargetsDecision' && d.targetRequirements?.length === 1) {
      const r = d.targetRequirements[0];
      const allowed = new Set(['index','description','minTargets','maxTargets']);
      const constrained = Object.entries(r).some(([k,v]) => !allowed.has(k) && v != null && v !== false);
      if (!constrained && r.maxTargets === 1 && [0,1].includes(r.minTargets)) {
        if (r.minTargets === 0) response({ type: 'TargetsResponse', selectedTargets: { [r.index]: [] } });
        for (const id of ids(d.legalTargets?.[r.index]).slice(0, 24))
          response({ type: 'TargetsResponse', selectedTargets: { [r.index]: [id] } });
      }
    }
    return out;
  }
  if (d && (!d.id || d.playerId !== playerId)) return [];
  const addOffered = action => d ? add({ type: 'SubmitDecision', playerId,
    response: { type: 'PlayCardResponse', decisionId: d.id, action: clone(action) } }) : add(action);
  const offered = Array.isArray(packet.legalActions) ? packet.legalActions : [];
  // Preserve deterministic preference as the first candidate for provider failure.
  const preferred = selectAction(packet);
  // selectAction already constructs legally shaped combat declarations from host
  // validAttackers/attackTargets and mandatoryBlockerAssignments. The remote
  // candidate adapter must not discard them: combat snapshots otherwise pause
  // forever with unsupported-decision-or-action despite a safe declaration.
  // A PlayCardDecision can still ONLY wrap a land or spell.
  if (preferred && preferred.action.playerId === playerId &&
      (!d || ['PlayLand','CastSpell'].includes(preferred.action.type)) &&
      (!['DeclareAttackers','DeclareBlockers'].includes(preferred.action.type) ||
        offered.some(info => info?.action?.type === preferred.action.type &&
          info.action.playerId === playerId))) addOffered(preferred.action);
  for (const info of offered.slice(0, 128)) {
    const a = info?.action;
    if (a?.playerId !== playerId || !simple(info) || info.requiresTargets || info.targetRequirements?.length) continue;
    if (!['PlayLand','CastSpell','PassPriority'].includes(a.type) || (d && a.type === 'PassPriority')) continue;
    if (!out.some(x => JSON.stringify(x.action) === JSON.stringify(a))) addOffered(a);
  }
  return out;
}
/** Public-only diagnostic category: no card/target IDs or private game payload. */
export function unsupportedReason(packet) {
  const valid = type => typeof type === 'string' && /^[A-Za-z][A-Za-z0-9]{0,47}$/.test(type);
  const d = packet?.pendingDecision;
  if (d != null) return 'unsupported-decision-or-action:decision-' +
    (valid(d.type) ? d.type : 'unknown');
  const offered = Array.isArray(packet?.legalActions) ? packet.legalActions : [];
  const types = [...new Set(offered.map(x => x?.action?.type).filter(valid))].slice(0, 3);
  return 'unsupported-decision-or-action:actions-' + (types.join(',') || 'none');
}

export function freshSession(id, seat, deckId, now) {
  return { id, seat, deckId, link: id, seq: 0, hostLink: '', hostSeq: 0, epoch: -1,
    status: 'joining', reason: null, receipts: 0, accepted: false, ready: false, actions: 0, failures: 0, reservedTokens: 0,
    expires: now + 4 * 60 * 60 * 1000, awaiting: null, lastTurn: null, transitions: 0, acceptedVersion: null };
}
export function receive(session, packet, now) {
  if (session.expires <= now || ['stopped','error'].includes(session.status)) return null;
  if (packet.type === 'error') {
    session.failures++;
    session.reason = 'host-rejected';
    session.status = 'needs attention';
    session.awaiting = null;
    return null; // no blind retry of a host-rejected action
  }
  if (packet.type === 'actionReceipt') {
    if (session.awaiting && packet.accepted === true && packet.interactionEpoch === 'offline-'+session.awaiting.epoch) {
      session.receipts++;
      // The receipt arrives BEFORE the next host snapshot. Retain its version so
      // Control can count real subsequent updates rather than showing zero forever.
      session.acceptedVersion = session.awaiting.version;
      session.awaiting = null;
    }
    return null;
  }
  if (packet.type === 'lanSeat') {
    if (packet.strictDeck !== true) { session.status='needs attention';session.reason='host-must-support-strict-deck';session.failures++;return null; }
    session.accepted = true;
    session.status = 'ready';
    if (!session.ready) { session.ready = true; return { type: 'lanReady', ready: true }; }
  }
  if (packet.type === 'mulliganDecision') {
    if (session.mulligan) return null;
    session.mulligan = true;
    return { type: 'keepHand' };
  }
  if (packet.type === 'chooseBottomCards') {
    if (session.bottomed) return null;
    const cardIds = chooseBottomCards(packet);
    if (cardIds == null) { session.status = 'needs attention'; return null; }
    session.bottomed = true;
    return { type: 'chooseBottomCards', cardIds };
  }
  if (packet.type === 'gameOver' || packet.type === 'arcanaBye') { session.status = 'stopped'; return null; }
  if (packet.type !== 'stateUpdate' || !session.accepted || session.failures) return null;
  const epoch = /^offline-(\d+)$/.exec(String(packet.interactionEpoch));
  if (!epoch || !Number.isSafeInteger(Number(epoch[1])) || Number(epoch[1]) <= session.epoch) return null;
  session.epoch = Number(epoch[1]);
  if (Number.isSafeInteger(session.acceptedVersion) && packet.stateVersion > session.acceptedVersion) {
    session.transitions++;
    session.acceptedVersion = null;
    session.awaiting = null;
  } else if (session.awaiting && packet.stateVersion > session.awaiting.version) {
    // A new host snapshot also proves progress even when an older APK did not
    // send actionReceipt. This does not claim the action was accepted.
    session.transitions++;
    session.awaiting = null;
  }
  session.lastTurn = packet.state?.turnNumber ?? null;
  if (packet.interactive !== true) return null;
  session.status = session.actions >= 2000 ? 'needs attention' : 'playing';
  return null;
}
export function publicStatus(session) {
  if (!session) return { status: 'idle', model: 'off' };
  return { sessionId: session.id, seat: session.seat, deckId: session.deckId, status: session.status,
    actionsSubmitted: session.actions, actionsAccepted: session.receipts, reason: session.reason, observedTransitions: session.transitions, lastTurn: session.lastTurn,
    awaitingHost: !!session.awaiting, reservedTokens: session.reservedTokens, chatMode: session.chatMode ?? 'muted', banter: session.lastChatSource ?? (session.model ? 'model-ready' : 'canned'), model: session.model ? 'opted in' : 'off' };
}
