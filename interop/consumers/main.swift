import Foundation
import OpenCCInterop

func bytes(_ data: Data) -> KotlinByteArray {
    let result = KotlinByteArray(size: Int32(data.count))
    for (index,value) in data.enumerated() { result.set(index: Int32(index), value: Int8(bitPattern:value)) }
    return result
}
let resources=OpenCCResources()
resources.put(name: "characters.ocd2", data: bytes(try Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[1]))))
let converter=try resources.loadJson(json: """
{"segmentation":{"type":"mmseg","dict":{"type":"ocd2","file":"characters.ocd2"}},"conversion_chain":[{"dict":{"type":"ocd2","file":"characters.ocd2"}}]}
""", configDirectory:"", includeTofuRiskDictionaries:true)
resources.clear()
let output=try converter.convert(text:"😀汉字转换")
precondition(output=="😀漢字轉換")
let range=try converter.convertRange(text:"😀汉",startIndex:2,endIndex:3)
precondition(range=="漢")
var rejected=false
 do { _ = try converter.convertRange(text:"😀汉",startIndex:1,endIndex:3) } catch { rejected=true }
precondition(rejected)
let prefix=try converter.convertPrefix(text:"汉字转换",length:2)
precondition(prefix=="漢字")
let converted=try converter.convertUtf8(bytes:bytes(Data("汉".utf8)))
let encoded=(0..<converted.size).map { UInt8(bitPattern:converted.get(index:$0)) }
precondition(String(decoding:encoded,as:UTF8.self)=="漢")
let inspection=try converter.inspect(text:"汉字")
precondition(inspection.output=="漢字" && inspection.stages.size==1)
let mapped=try converter.convertWithSegments(text:"😀汉")
let last=mapped.segments.get(index:mapped.segments.size-1)!
precondition(last.sourceEnd==3 && last.targetEnd==3)
let stream=try converter.stream(maxKeepChars:16)
let chunk=try stream.convertChunk(text:"汉字")
precondition(chunk=="")
converter.close()
rejected=false
 do { _ = try converter.convert(text:"汉") } catch { rejected=true }
precondition(rejected)
let flushed=try stream.finish(text:"转换")
precondition(flushed=="漢字轉換")
let empty=try stream.finish(text:"")
precondition(empty=="")
rejected=false
 do { _ = try resources.load(configName:"missing",includeTofuRiskDictionaries:true) } catch {
    rejected=true
    precondition(error.localizedDescription.contains("missing.json"))
}
precondition(rejected)
print("PASS focused Swift exports, binary dictionary, errors, inspection, map and streaming")
