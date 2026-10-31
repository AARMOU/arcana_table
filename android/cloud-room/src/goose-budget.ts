import { DurableObject } from 'cloudflare:workers';
import { modelDecision, modelChat } from '../../../tools/goosegeta/model.mjs';
export interface BudgetEnv {
  GOOSEGETA_OPENAI_ENABLED?: string;
  OPENAI_API_KEY?: string;
  GOOSEGETA_DAILY_TOKENS?: string;
  GOOSEGETA_CANARY_ONE_SHOT?: string;
  GOOSEGETA_TOTAL_TOKENS?: string;
}
/** One account-wide ledger, shared by every room. Internal DO binding only. */
export class GooseBudget extends DurableObject<BudgetEnv> {
  async fetch(request: Request): Promise<Response> {
    if (request.method !== 'POST' || this.env.GOOSEGETA_OPENAI_ENABLED !== 'true' || !this.env.OPENAI_API_KEY)
      return Response.json({error:'model-disabled'}, {status:503});
    const input = await request.json() as { reserve: number; body: any; options: any[]; kind?: string };
    const minimum = new TextEncoder().encode(JSON.stringify(input.body ?? {})).length + 256 + 4096;
    if (input.body?.max_output_tokens !== 256 || input.reserve < minimum || input.body?.store !== false || input.body?.tools?.length !== 0)
      return Response.json({error:'request-budget-mismatch'}, {status:400});
    const cap = Number(this.env.GOOSEGETA_DAILY_TOKENS);
    if (!Number.isSafeInteger(cap) || cap < 1 || cap > 1000000 || !Number.isSafeInteger(input.reserve) || input.reserve < 1)
      return Response.json({error:'budget-config'}, {status:503});
    // Luna's published standard rate is <= $0.50 per million tokens in either direction.
    // Count a conservative UTF-8-bytes-plus-buffer reservation for EACH provider attempt.
    // A 9M lifetime reservation cap bounds this isolated model to <= $4.50 at that
    // rate (ignoring other account usage), independent of UTC daily resets.
    const lifetimeCap = Number(this.env.GOOSEGETA_TOTAL_TOKENS ?? '1000000');
    if (!Number.isSafeInteger(lifetimeCap) || lifetimeCap < 1 || lifetimeCap > 9000000)
      return Response.json({error:'total-budget-config'}, {status:503});
    const day = new Date().toISOString().slice(0,10);
    const oneShot = this.env.GOOSEGETA_CANARY_ONE_SHOT === 'true';
    const allowed = await this.ctx.storage.transaction(async tx => {
      // Persistent across UTC days and durable-object reconstruction: at most one paid provider attempt.
      if (oneShot && await tx.get<boolean>('goosegeta-ever-used-canary-v1')) return false;
      const lifetime = (await tx.get<number>('goosegeta-lifetime-reserved-v1')) ?? 0;
      if (!Number.isSafeInteger(lifetime) || lifetime < 0 || lifetime + input.reserve > lifetimeCap) return false;
      const ledger = await tx.get<{day:string;tokens:number;failed:boolean}>('ledger');
      const tokens = ledger?.day === day ? ledger.tokens : 0;
      if ((ledger?.day === day && ledger.failed) || tokens + input.reserve > cap) return false;
      await tx.put('ledger', {day, tokens:tokens+input.reserve, failed:false});
      await tx.put('goosegeta-lifetime-reserved-v1', lifetime + input.reserve);
      if (oneShot) await tx.put('goosegeta-ever-used-canary-v1', true);
      return true;
    });
    if (!allowed) return Response.json({error:'daily-limit-or-circuit'}, {status:429});
    try {
      const result = input.kind === "chat" ? await modelChat(this.env.OPENAI_API_KEY, input.body) : await modelDecision(this.env.OPENAI_API_KEY, input.body, input.options);
      if (!Number.isSafeInteger(result.usage) || result.usage < 0 || result.usage > input.reserve) throw Error('usage-bound');
      return Response.json(result);
    } catch {
      await this.ctx.storage.transaction(async tx => {
        const ledger = await tx.get<any>('ledger');
        if (ledger?.day === day) await tx.put('ledger', {...ledger,failed:true});
      });
      return Response.json({error:'model-paused'}, {status:503});
    }
  }
}
