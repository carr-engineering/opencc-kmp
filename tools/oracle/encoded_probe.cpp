// Test-only encoded-input audit of the pinned reference; never a runtime binding.
#include "Config.hpp"
#include "Converter.hpp"
#include "ConversionInspection.hpp"
#include "Exception.hpp"
#include "SimpleConverter.hpp"
#include <iostream>
#include <sstream>
#include <vector>

void emitHex(std::string_view text) {
  const char* digits = "0123456789abcdef";
  for (unsigned char byte : text) std::cout << digits[byte >> 4] << digits[byte & 15];
}
void jsonHex(std::ostream& out, std::string_view text) {
  const char* digits = "0123456789abcdef";
  out << '"';
  for (unsigned char byte : text) out << digits[byte >> 4] << digits[byte & 15];
  out << '"';
}
void jsonSegments(std::ostream& out, const std::vector<std::string>& values) {
  out << '[';
  for (size_t i = 0; i < values.size(); i++) { if (i) out << ','; jsonHex(out, values[i]); }
  out << ']';
}
void jsonInspection(std::ostream& out, const opencc::ConversionInspectionResult& result) {
  out << "{\"input\":"; jsonHex(out, result.input);
  out << ",\"output\":"; jsonHex(out, result.output);
  out << ",\"segments\":"; jsonSegments(out, result.segments);
  out << ",\"stages\":[";
  for (size_t i = 0; i < result.stages.size(); i++) {
    if (i) out << ',';
    out << "{\"index\":" << result.stages[i].index << ",\"segments\":";
    jsonSegments(out, result.stages[i].segments); out << '}';
  }
  out << "],\"pipelineStages\":[";
  for (size_t i = 0; i < result.pipelineStages.size(); i++) { if (i) out << ','; jsonInspection(out, result.pipelineStages[i]); }
  out << "]}";
}
std::string fromHex(const std::string& value) {
  std::string result;
  for (size_t i = 0; i + 1 < value.size(); i += 2) result.push_back(static_cast<char>(std::stoul(value.substr(i, 2), nullptr, 16)));
  return result;
}
int main(int argc, char** argv) {
  if (argc != 3) return 2;
  opencc::Config config;
  auto converter = config.NewFromFile(argv[1], std::vector<std::string>{argv[2]}, nullptr);
  opencc::SimpleConverter simple(argv[1], std::vector<std::string>{argv[2]});
  std::string line;
  while (std::getline(std::cin, line)) {
    std::istringstream fields(line);
    std::string operation, length, encoded;
    std::getline(fields, operation, '\t'); std::getline(fields, length, '\t'); std::getline(fields, encoded);
    const auto input = fromHex(encoded);
    const size_t count = length == "-1" ? static_cast<size_t>(-1) : std::stoul(length);
    try {
      std::string output;
      std::ostringstream trace;
      size_t reported;
      if (operation == "view") {
        output = converter->Convert(std::string_view(input).substr(0, count));
        reported = output.size();
      } else if (operation == "simple") {
        output = simple.Convert(input.c_str(), count); reported = output.size();
      } else if (operation == "cstring") {
        output = simple.Convert(input.c_str()); reported = output.size();
      } else if (operation == "buffer") {
        std::vector<char> buffer(input.size() * 32 + 4096, '\0');
        reported = simple.Convert(input.c_str(), count, buffer.data());
        output.assign(buffer.data(), reported);
      } else if (operation == "inspect") {
        const auto result = converter->Inspect(std::string_view(input).substr(0, count));
        output = result.output; reported = output.size(); jsonInspection(trace, result);
      } else if (operation == "stream0" || operation == "stream16") {
        opencc::ConverterStream stream(converter, operation == "stream0" ? 0 : 16);
        std::vector<std::string> chunks;
        for (size_t start = 0; start < input.size(); start += count)
          chunks.push_back(stream.ConvertChunk(std::string_view(input).substr(start, count)));
        chunks.push_back(stream.Finish());
        for (const auto& chunk : chunks) output += chunk;
        reported = output.size(); jsonSegments(trace, chunks);
      } else return 3;
      std::cout << "OK\t" << reported << '\t'; emitHex(output);
      if (!trace.str().empty()) std::cout << '\t' << trace.str();
      std::cout << '\n';
    } catch (const opencc::Exception& error) {
      std::cout << "ERROR\t"; emitHex(error.what()); std::cout << '\n';
    } catch (const std::exception& error) {
      std::cout << "ERROR\t"; emitHex(error.what()); std::cout << '\n';
    }
  }
}
