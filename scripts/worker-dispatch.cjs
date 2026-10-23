'use strict';
const {dispatchLimit}=require('./usage-gate.cjs');
const isCopilot=a=>['copilot','copilot-swe-agent[bot]'].includes(a.login?.toLowerCase());

// Claims are a conservative capacity bound, never a claim that sessions are running.
function planDispatch({budget,ready=false,ciReady=false,issues=[],prs=[],testIssue='',maxClaims=2}) {
  if(!ready || !ciReady)return {state:'readiness-blocked',issues:[]};
  if(testIssue && !/^\d+$/.test(String(testIssue)))return {state:'invalid-test-issue',issues:[]};
  if(!Number.isInteger(maxClaims)||maxClaims<1||maxClaims>2)return {state:'capacity-invalid',issues:[]};
  const claims=issues.filter(i=>!i.pull_request && i.assignees?.some(isCopilot)).length;
  const capacity=Math.max(0,maxClaims-claims);
  const cap=Math.min(capacity,dispatchLimit(budget,{testIssue:!!testIssue}));
  const eligible=issues.filter(i=>!i.pull_request && i.state==='open' &&
    i.title.startsWith('[Arcana Queue]') && !(i.assignees||[]).length &&
    (!testIssue||String(i.number)===String(testIssue)) &&
    !prs.some(p=>p.state==='open' && new RegExp(`(?:#${i.number}\\b|/issues/${i.number}\\b)`).test(p.body||'')))
    .sort((a,b)=>a.number-b.number);
  return {state:cap?'planned':'budget-or-capacity-blocked',claims,
    issues:eligible.slice(0,cap).map(i=>i.number)};
}
module.exports={planDispatch};
