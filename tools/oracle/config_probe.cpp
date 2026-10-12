// Test-only config/schema audit of the pinned native reference.
#include "Config.hpp"
#include "Converter.hpp"
#include "Exception.hpp"
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>

void hex(std::string_view value) {
  const char* digits = "0123456789abcdef";
  for (unsigned char byte : value) std::cout << digits[byte >> 4] << digits[byte & 15];
}
int main(int argc, char** argv) {
  if (argc != 5) return 2;
  try {
    opencc::Config config;
    opencc::ConfigLoadOptions options;
    options.includeTofuRiskDictionaries = std::string(argv[3]) == "true";
    opencc::ConverterPtr converter;
    if (std::string(argv[2]) == "file") {
      converter = config.NewFromFile(argv[1], std::vector<std::string>{argv[4]}, nullptr, options);
    } else {
      std::ifstream input(argv[1], std::ios::binary);
      const std::string json((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
      converter = config.NewFromString(json, std::vector<std::string>{argv[4]}, options);
    }
    std::cout << "OK\t"; hex(converter->Convert("汉abc神里")); std::cout << '\n';
  } catch (const opencc::Exception& error) {
    const char* kind = dynamic_cast<const opencc::InvalidFormat*>(&error) ? "InvalidFormat" :
                       dynamic_cast<const opencc::InvalidUTF8*>(&error) ? "InvalidUTF8" :
                       dynamic_cast<const opencc::FileNotFound*>(&error) ? "FileNotFound" :
                       dynamic_cast<const opencc::ShouldNotBeHere*>(&error) ? "ShouldNotBeHere" : "OpenCCException";
    std::cout << "ERROR\t" << kind << '\t'; hex(error.what()); std::cout << '\n';
  } catch (const std::exception& error) {
    std::cout << "ERROR\tstd::exception\t"; hex(error.what()); std::cout << '\n';
  }
}
