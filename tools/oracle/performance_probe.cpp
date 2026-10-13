// Test-only in-process benchmark of the pinned native conversion engine.
#include "Config.hpp"
#include "Converter.hpp"
#include <algorithm>
#include <chrono>
#include <fstream>
#include <iostream>
#include <iterator>
#include <numeric>
#include <string>
#include <vector>

int main(int argc, char** argv) {
  if (argc != 6) return 2;
  try {
    const int iterations = std::stoi(argv[4]);
    if (iterations < 2) return 2;
    std::ifstream input(argv[3], std::ios::binary);
    if (!input) return 3;
    const std::string text((std::istreambuf_iterator<char>(input)), {});
    opencc::Config config;
    auto converter = config.NewFromFile(argv[1], std::vector<std::string>{argv[2]}, nullptr);
    auto output = converter->Convert(text);
    std::ofstream expected(argv[5], std::ios::binary);
    expected.write(output.data(), output.size());
    expected.close();
    if (!expected) return 3;
    for (int i = 0; i < 5; ++i) output = converter->Convert(text);
    std::vector<double> samples;
    size_t consumed = 0;
    for (int i = 0; i < iterations; ++i) {
      const auto start = std::chrono::steady_clock::now();
      output = converter->Convert(text);
      const auto stop = std::chrono::steady_clock::now();
      consumed += output.size();
      samples.push_back(std::chrono::duration<double, std::milli>(stop - start).count());
    }
    std::cout << "{\"input_bytes\":" << text.size() << ",\"iterations\":" << iterations
              << ",\"mean_ms\":" << std::accumulate(samples.begin(), samples.end(), 0.0) / samples.size()
              << ",\"consumed_bytes\":" << consumed << ",\"samples_ms\":[";
    for (size_t i = 0; i < samples.size(); ++i) { if (i) std::cout << ','; std::cout << samples[i]; }
    std::cout << "]}\n";
    return 0;
  } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 4; }
}
