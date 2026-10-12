import assert from 'node:assert/strict';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
const { OpenCCResources } = await import(pathToFileURL(process.argv[2]).href);
const resources = new OpenCCResources();
const binary = fs.readFileSync(process.argv[3]);
resources.put('characters.ocd2', new Int8Array(binary.buffer, binary.byteOffset, binary.byteLength));
binary.fill(0);
const converter = resources.loadJson(JSON.stringify({
  segmentation: {type:'mmseg',dict:{type:'ocd2',file:'characters.ocd2'}},
  conversion_chain:[{dict:{type:'ocd2',file:'characters.ocd2'}}]
}), '', true);
resources.clear();
assert.equal(converter.convert('😀汉字转换'),'😀漢字轉換');
assert.equal(converter.convertRange('😀汉',2,3),'漢');
assert.throws(()=>converter.convertRange('😀汉',1,3));
assert.equal(converter.convertPrefix('汉字转换',2),'漢字');
const bytes = new TextEncoder().encode('汉');
const converted = converter.convertUtf8(new Int8Array(bytes.buffer));
assert(converted instanceof Int8Array);
assert.equal(new TextDecoder().decode(converted),'漢');
const inspection = converter.inspect('汉字');
assert.equal(inspection.output,'漢字');
assert.equal(inspection.stages.length,1);
const mapped = converter.convertWithSegments('😀汉');
assert.equal(mapped.segments.at(-1).sourceEnd,3);
assert.equal(mapped.segments.at(-1).targetEnd,3);
const stream=converter.stream(16);
assert.equal(stream.convertChunk('汉字'),'');
converter.close();
assert.throws(()=>converter.convert('汉'));
assert.equal(stream.finish('转换'),'漢字轉換');
assert.equal(stream.finish(''),'');
assert.throws(()=>resources.load('missing',true),error=>String(error.message).includes('missing.json'));
console.log('PASS focused JavaScript exports, binary dictionary, typed arrays, errors, inspection, map and streaming');
