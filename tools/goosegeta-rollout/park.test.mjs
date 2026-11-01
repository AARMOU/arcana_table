import test from 'node:test';
import assert from 'node:assert/strict';
import {planBindings} from './park.mjs';
test('parking changes only approved flags and inherits every unrelated/secret binding without exposing values',()=>{
 const input=[{name:'OPENAI_API_KEY',type:'secret_text'},{name:'ROOMS',type:'durable_object_namespace',namespace_id:'original'},
 {name:'GOOSEGETA_ENABLED',type:'plain_text',text:'true'},{name:'GOOSEGETA_OPENAI_ENABLED',type:'plain_text',text:'true'},
 {name:'OTHER',type:'plain_text',text:'retain-exactly'}];
 const plan=planBindings('arcana-table-rooms-goosegeta-test',input);
 assert.deepEqual(plan,[{name:'OPENAI_API_KEY',type:'inherit'},{name:'ROOMS',type:'inherit'},{name:'OTHER',type:'inherit'},
 {name:'GOOSEGETA_OPENAI_ENABLED',type:'plain_text',text:'false'},{name:'GOOSEGETA_ENABLED',type:'plain_text',text:'false'}]);
 assert.equal(input[2].text,'true');
 assert.throws(()=>planBindings('arcana-table-rooms',input));
 assert.deepEqual(planBindings('arcana-review',[{name:'SESSION_SECRET',type:'secret_text'}]),[{name:'SESSION_SECRET',type:'inherit'},{name:'GOOSEGETA_ENABLED',type:'plain_text',text:'false'}]);
});
