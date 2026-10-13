// Exact fixed-width/legacy/corrupt binary contract oracle for pinned OpenCC.
#include "BinaryDict.hpp"
#include "SerializedValues.hpp"
#include "DartsDict.hpp"
#include "MarisaDict.hpp"
#include "TextDict.hpp"
#include "Lexicon.hpp"
#include <iostream>
#include <string>
#include <memory>

static std::string bytes(const std::string& input) {
  std::string result;
  for (size_t i = 0; i < input.size(); i += 2)
    result.push_back(static_cast<char>(std::stoul(input.substr(i, 2), nullptr, 16)));
  return result;
}
static void hex(const std::string& value) {
  const char* digits = "0123456789abcdef";
  for (unsigned char byte : value) std::cout << digits[byte >> 4] << digits[byte & 15];
}
int main(int argc, char** argv) {
  if (argc != 2) return 2;
  std::string operation = argv[1];
  const bool fileMode = operation.size() > 5 && operation.substr(operation.size() - 5) == "-file";
  if (fileMode) operation.resize(operation.size() - 5);
  if (operation == "make65") {
    std::string text;
    for (int i = 1; i <= 65; ++i) text += std::string(i, 'a') + "\tv" + std::to_string(i) + "\n";
    auto dict = opencc::DartsDict::NewFromDict(*opencc::TextDict::NewFromBuffer(text.data(), text.size()));
    FILE* file = tmpfile();
    dict->SerializeToFile(file);
    rewind(file);
    std::string image;
    int byte;
    while ((byte = fgetc(file)) != EOF) image.push_back(static_cast<char>(byte));
    fclose(file);
    hex(image); std::cout << '\n';
    return 0;
  }
  std::string line;
  while (std::getline(std::cin, line)) {
    auto image = bytes(line);
    try {
      std::unique_ptr<FILE, decltype(&fclose)> source(fileMode ? tmpfile() : nullptr, fclose);
      if (source) { fwrite(image.data(), 1, image.size(), source.get()); rewind(source.get()); }
      opencc::LexiconPtr lexicon;
      if (operation == "binary") lexicon = (source ? opencc::BinaryDict::NewFromFile(source.get()) : opencc::BinaryDict::NewFromBuffer(image.data(), image.size()))->GetLexicon();
      else if (operation == "values") lexicon = (source ? opencc::SerializedValues::NewFromFile(source.get()) : opencc::SerializedValues::NewFromBuffer(image.data(), image.size(), nullptr))->GetLexicon();
      else if (operation == "marisa") lexicon = (source ? opencc::MarisaDict::NewFromFile(source.get()) : opencc::MarisaDict::NewFromBuffer(image.data(), image.size()))->GetLexicon();
      else {
        auto dict = source ? opencc::DartsDict::NewFromFile(source.get()) : opencc::DartsDict::NewFromBuffer(image.data(), image.size());
        if (operation == "serialize") {
          FILE* file = tmpfile();
          try { dict->SerializeToFile(file); } catch (...) { fclose(file); throw; }
          fclose(file); std::cout << "OK\n"; continue;
        }
        if (operation == "prefix65") {
          auto result = dict->MatchPrefix(std::string(65, 'a').c_str(), 65);
          std::cout << "OK|"; hex(result.Get()->GetDefault()); std::cout << '\n'; continue;
        }
        lexicon = dict->GetLexicon();
      }
      std::cout << "OK";
      for (const auto& entry : *lexicon) {
        std::cout << '|'; hex(entry->Key()); std::cout << ':';
        for (size_t i = 0; i < entry->NumValues(); ++i) { if (i) std::cout << ','; hex(entry->Values()[i]); }
      }
    } catch (const opencc::InvalidFormat&) { std::cout << "InvalidFormat"; }
      catch (const opencc::Exception&) { std::cout << "OtherError"; }
    std::cout << '\n';
  }
}
