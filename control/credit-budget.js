(()=>{'use strict';
const $=id=>document.getElementById(id);
function parse(raw){
 if(!/^(0|[1-9]\d*)$/.test(String(raw)))return null;
 const n=Number(raw);
 return Number.isSafeInteger(n)&&n<=10000000?n:null;
}
function update(){
 const included=parse($('included').value.trim()),extra=parse($('extra').value.trim());
 const total=included===null||extra===null?null:included+extra;
 if(total===null||total<1||!Number.isSafeInteger(total)){
  $('total').value='';$('copy').disabled=true;
  $('reserve').textContent='Enter confirmed whole-number quantities in both fields.';
  $('message').textContent='';return;
 }
 $('total').value=String(total);
 $('copy').disabled=false;
 $('reserve').textContent='Default 10% reserve: '+Math.ceil(total*0.1).toLocaleString()+' AI credits. This is a calculation, not a verified GitHub setting.';
 $('message').textContent='';
}
for(const id of ['included','extra'])$(id).addEventListener('input',update);
$('copy').addEventListener('click',async()=>{
 const value=$('total').value;
 if(!value||$('copy').disabled)return;
 try{
  if(!navigator.clipboard?.writeText)throw Error('Clipboard unavailable');
  await navigator.clipboard.writeText(value);
  $('message').textContent='Copied '+value+'. Paste it into GitHub Actions variable ARCANA_AI_CREDIT_BUDGET and save.';
 }catch(e){
  $('total').focus();$('total').select();
  $('message').textContent='Select and copy the highlighted value, then save it in GitHub Actions variables.';
 }
});
update();
})();