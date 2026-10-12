// Test-only resource contract probe of the pinned upstream reference.
#include "ResourceProvider.hpp"
#include "Exception.hpp"
#include <iostream>
#include <string>
#include <vector>

void hex(const char* value, size_t size) {
  const char* digits = "0123456789abcdef";
  for (size_t i = 0; i < size; i++) {
    unsigned char byte = static_cast<unsigned char>(value[i]);
    std::cout << digits[byte >> 4] << digits[byte & 15];
  }
}
void hex(const std::string& value) { hex(value.data(), value.size()); }
int main(int argc, char** argv) {
  if (argc < 3) return 2;
  std::vector<std::string> roots;
  for (int i = 3; i < argc; i++) roots.emplace_back(argv[i]);
  try {
    opencc::FilesystemResourceProvider provider(roots);
    const std::string operation(argv[1]);
    if (operation == "resolve") {
      const std::string path = provider.Resolve(argv[2]);
      std::cout << "OK\t";
      hex(path);
    } else if (operation == "resource") {
      auto resource = provider.GetResource(argv[2]);
      std::cout << "OK\t";
      hex(resource->Name());
      std::cout << '\t' << resource->Size() << '\t';
      hex(resource->Data(), resource->Size());
      std::cout << '\t' << (resource->CacheKey() == provider.GetResource(argv[2])->CacheKey());
    } else return 2;
    std::cout << '\n';
  } catch (const opencc::Exception& error) {
    std::cout << "ERROR\t";
    hex(std::string(error.what()));
    std::cout << '\n';
  }
}
