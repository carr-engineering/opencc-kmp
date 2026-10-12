// Test-only builder for exact merged metadata strings that text dictionaries cannot encode.
#include "DictEntry.hpp"
#include "Lexicon.hpp"
#include "MarisaDict.hpp"
#include "SerializableDict.hpp"
#include "TextDict.hpp"
#include <memory>
#include <string>
#include <vector>
int main(int argc, char** argv) {
  if (argc != 4) return 2;
  std::string token;
  const std::string encoded(argv[2]);
  for (size_t i = 0; i + 1 < encoded.size(); i += 2)
    token.push_back(static_cast<char>(std::stoul(encoded.substr(i, 2), nullptr, 16)));
  const std::string kind(argv[3]);
  auto lexicon = std::make_shared<opencc::Lexicon>();
  lexicon->Add(opencc::DictEntryFactory::New("勇敢", std::vector<std::string>{kind == "base" ? token : "10", "n", "base"}));
  lexicon->Add(opencc::DictEntryFactory::New("的", std::vector<std::string>{"1", "x", "base"}));
  lexicon->Add(opencc::DictEntryFactory::New("士兵", std::vector<std::string>{"10", "n", "base"}));
  if (kind != "base") lexicon->Add(opencc::DictEntryFactory::New("未", std::vector<std::string>{token, "n", kind}));
  lexicon->Sort();
  auto dictionary = opencc::MarisaDict::NewFromDict(opencc::TextDict(lexicon));
  static_cast<opencc::SerializableDict&>(*dictionary).SerializeToFile(std::string(argv[1]));
}
