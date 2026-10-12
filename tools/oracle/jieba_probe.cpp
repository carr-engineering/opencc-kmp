// Test-only pinned Jieba segmentation and plugin/config operations.
#include "Config.hpp"
#include "Converter.hpp"
#include "Exception.hpp"
#include "JiebaSegmentation.hpp"
#include "Segments.hpp"
#include <iostream>
#include <memory>
#include <string>

std::string unhex(const std::string& value) {
  std::string result;
  for (size_t i=0; i+1<value.size(); i+=2) result.push_back(static_cast<char>(std::stoul(value.substr(i,2), nullptr,16)));
  return result;
}
std::string hex(std::string_view value) {
  const char* digits="0123456789abcdef"; std::string result;
  for(unsigned char c:value) { result+=digits[c>>4]; result+=digits[c&15]; }
  return result;
}
int main(int argc,char** argv) {
  if(argc!=6) return 2;
  std::cout << std::unitbuf;
  try {
    std::unique_ptr<opencc::JiebaSegmentation> segment;
    opencc::ConverterPtr converter;
    if(std::string(argv[1])=="segment") segment.reset(new opencc::JiebaSegmentation(argv[2],argv[3],argv[4],argv[5]+std::string("/idf.utf8"),argv[5]+std::string("/stop_words.utf8")));
    else {
      opencc::Config config; opencc::ConfigLoadOptions options;
      options.includeTofuRiskDictionaries=std::string(argv[4])=="true";
      converter=config.NewFromFile(argv[2],{argv[3]},nullptr,options);
    }
    std::string line;
    while(std::getline(std::cin,line)) {
      const auto text=unhex(line);
      try {
        std::string output;
        if(segment) {
          const auto result=segment->Segment(text);
          output=std::to_string(result->Length())+'\t';
          for(size_t i=0;i<result->Length();++i) { if(i) output+=','; output+=hex(result->At(i)); }
        } else if(std::string(argv[1]).find("stream")==0) {
          const std::string mode(argv[1]); const auto split=mode.find(':');
          const size_t keep=std::stoul(mode.substr(6,split-6)), stride=std::stoul(mode.substr(split+1));
          opencc::ConverterStream stream(converter,keep);
          const size_t count=(text.size()+stride-1)/stride+1;
          output=std::to_string(count)+'\t';
          size_t index=0;
          for(size_t start=0;start<text.size();start+=stride) { if(index++) output+=','; output+=hex(stream.ConvertChunk(std::string_view(text).substr(start,stride))); }
          if(index) output+=','; output+=hex(stream.Finish());
        } else output=hex(converter->Convert(text));
        std::cout << "OK\t" << output << '\n';
      } catch(const opencc::Exception& e) {
        const char* kind=dynamic_cast<const opencc::InvalidUTF8*>(&e)?"InvalidUTF8":"OpenCCException";
        std::cout << "ERROR\t" << kind << '\t' << hex(e.what()) << '\n';
      } catch(const std::exception& e) { std::cout << "ERROR\tstd::exception\t" << hex(e.what()) << '\n'; }
    }
  } catch(const opencc::Exception& e) { std::cerr << e.what() << '\n'; return 3; }
    catch(const std::exception& e) { std::cerr << e.what() << '\n'; return 3; }
}
