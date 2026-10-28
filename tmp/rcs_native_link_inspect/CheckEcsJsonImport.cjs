// Execute only the inspected import routine from the locally downloaded UI bundle.
// No browser, server, database, or transport request is used.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const source = fs.readFileSync('tmp/fleet_api/ruleDetail--Zy6BsBk.js', 'utf8');
const start = source.indexOf('importSetData(e,a){');
const end = source.indexOf('},addJsonRow(e){', start) + 1;
assert(start >= 0 && end > start);
const importer = eval('({' + source.slice(start, end) + '})');
importer.parametersDefine = [];
importer.getRadom = () => 'offline-only';
const request = JSON.parse(fs.readFileSync('SECS/native_transfer/manual_api/ecs_transfer_candidate_89_to_97.json', 'utf8'));
const fields = [];
importer.importSetData(request, fields);
for (const key of ['missionData', 'robotIds', 'robotModels']) {
    const field = fields.find(x => x.externalName === key);
    assert(field);
    assert.equal(field.type, '');
    assert.equal(field.children.length, 0);
    console.log(JSON.stringify({field: key, importedType: field.type, importedChildren: field.children.length}));
}
assert.equal(fields.find(x => x.externalName === 'orgId').type, 'STRING');
console.log('CONFIRMED: actual UI importer does not construct array fields. NO NETWORK REQUEST SENT.');
