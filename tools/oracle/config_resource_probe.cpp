// Test-only configuration/provider integration probe of pinned OpenCC.
#include "Config.hpp"
#include "Converter.hpp"
#include "Exception.hpp"
#include "ResourceProvider.hpp"
#include <iostream>
#include <memory>
#include <string>
#include <vector>
std::string unhex(const std::string& value) {
  std::string result;
  for (size_t i = 0; i + 1 < value.size(); i += 2)
    result.push_back(static_cast<char>(std::stoul(value.substr(i, 2), nullptr, 16)));
  return result;
}
std::string hex(const std::string& value) {
  const char* digits = "0123456789abcdef";
  std::string result;
  for (unsigned char byte : value) { result += digits[byte >> 4]; result += digits[byte & 15]; }
  return result;
}
int main(int argc, char** argv) {
  if (argc < 4) return 2;
  try {
    opencc::Config loader;
    opencc::ConfigLoadOptions options;
    options.includeTofuRiskDictionaries = std::string(argv[3]) == "true";
    std::vector<std::string> paths;
    for (int index = 4; index < argc; index++) paths.emplace_back(argv[index]);
    const std::string kind(argv[2]);
    opencc::ConverterPtr converter;
    if (kind == "none") converter = loader.NewFromFile(argv[1], paths, nullptr, options);
    else {
      std::shared_ptr<opencc::ResourceProvider> provider = kind == "zip"
        ? std::shared_ptr<opencc::ResourceProvider>(new opencc::ZipResourceProvider(paths.at(0)))
        : std::shared_ptr<opencc::ResourceProvider>(new opencc::FilesystemResourceProvider(paths));
      converter = loader.NewFromFile(argv[1], provider, options);
    }
    std::string line;
    while (std::getline(std::cin, line)) std::cout << "OK\t" << hex(converter->Convert(unhex(line))) << '\n';
  } catch (const opencc::Exception& error) {
    const char* kind = dynamic_cast<const opencc::FileNotFound*>(&error) ? "FileNotFound" :
      dynamic_cast<const opencc::InvalidFormat*>(&error) ? "InvalidFormat" : "OpenCCException";
    std::cout << "ERROR\t" << kind << '\t' << hex(error.what()) << '\n';
  }
}
