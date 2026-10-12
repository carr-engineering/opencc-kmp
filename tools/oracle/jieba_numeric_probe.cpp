// cppjieba HMM/base frequency parsing uses C atof on resource tokens.
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <string>

int main() {
  std::string line;
  while (std::getline(std::cin, line)) {
    std::string token;
    for (size_t i = 0; i + 1 < line.size(); i += 2)
      token.push_back(static_cast<char>(std::stoul(line.substr(i, 2), nullptr, 16)));
    const double number = std::atof(token.c_str());
    uint64_t bits;
    std::memcpy(&bits, &number, sizeof(bits));
    std::cout << std::hex << std::setfill('0') << std::setw(16) << bits << '\n';
  }
}
