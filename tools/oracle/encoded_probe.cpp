// Test-only encoded-input audit of the pinned reference; never a runtime binding.
#include "Config.hpp"
#include "Converter.hpp"
#include "Exception.hpp"
#include "SimpleConverter.hpp"
#include <iostream>
#include <sstream>
#include <vector>

void emitHex(std::string_view text) {
  const char* digits = "0123456789abcdef";
  for (unsigned char byte : text) std::cout << digits[byte >> 4] << digits[byte & 15];
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
      } else return 3;
      std::cout << "OK\t" << reported << '\t'; emitHex(output); std::cout << '\n';
    } catch (const opencc::Exception& error) {
      std::cout << "ERROR\t"; emitHex(error.what()); std::cout << '\n';
    } catch (const std::exception& error) {
      std::cout << "ERROR\t"; emitHex(error.what()); std::cout << '\n';
    }
  }
}
