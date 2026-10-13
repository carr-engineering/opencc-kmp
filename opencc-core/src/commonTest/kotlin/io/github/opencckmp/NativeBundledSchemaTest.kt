package io.github.opencckmp

import kotlin.test.*

class NativeBundledSchemaTest {
    @Test fun bundledConfigsMatchSchema() {
        for ((name, json) in configs) {
            val parsed = JsonParser(json).parse() as JsonValue.Object
            assertNull(ConfigSchema.warning(parsed), name)
        }
        assertEquals(25, configs.size)
    }
    private val configs = mapOf(
        "data/config/hk2s.json" to """{
  "name": "Traditional Chinese (Hong Kong variant) to Simplified Chinese",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": { "type": "ocd2", "file": "TSPhrases.ocd2" }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "HKVariantsRevPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariantsRev.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TSPhrases.ocd2" },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          { "type": "ocd2", "file": "TSCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/hk2sp.json" to """{
  "name": "Traditional Chinese (Hong Kong variant) to Simplified Chinese (with phrases)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": { "type": "ocd2", "file": "TSPhrases.ocd2" }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "HKPhrasesRev.ocd2" },
          { "type": "ocd2", "file": "HKVariantsRevPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariantsRev.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TSPhrases.ocd2" },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          { "type": "ocd2", "file": "TSCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/hk2t.json" to """{
  "name": "Traditional Chinese (Hong Kong variant) to Traditional Chinese (OpenCC Standard)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "HKVariantsRevPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariantsRev.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/jp2t.json" to """{
  "name": "New Japanese Kanji (Shinjitai) to Old Japanese Kanji (Kyūjitai)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "JPShinjitaiPhrases.ocd2" },
          { "type": "ocd2", "file": "JPShinjitaiCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/s2hk.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Hong Kong variant)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": {
      "type": "group",
      "match_policy": "union",
      "dicts": [
        { "type": "ocd2", "file": "STPhrases.ocd2" },
        { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
      ]
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              { "type": "ocd2", "file": "STPhrases.ocd2" },
              { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
            ]
          },
          { "type": "ocd2", "file": "STCharacters.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "HKVariantsPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariants.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/s2hkp.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Hong Kong variant, with phrases)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": {
      "type": "group",
      "match_policy": "union",
      "dicts": [
        { "type": "ocd2", "file": "STPhrases.ocd2" },
        { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
      ]
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              { "type": "ocd2", "file": "STPhrases.ocd2" },
              { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
            ]
          },
          { "type": "ocd2", "file": "STCharacters.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "HKPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariantsPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariants.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/s2t.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (OpenCC Standard)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              { "type": "ocd2", "file": "STPhrases.ocd2" },
              { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
            ]
          },
          { "type": "ocd2", "file": "STCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/s2tw.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Taiwan Standard)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": {
      "type": "group",
      "match_policy": "union",
      "dicts": [
        { "type": "ocd2", "file": "STPhrases.ocd2" },
        { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
      ]
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              { "type": "ocd2", "file": "STPhrases.ocd2" },
              { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
            ]
          },
          { "type": "ocd2", "file": "STCharacters.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TWVariantsPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariants.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/s2twp.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Taiwan Standard, with Taiwan Phrases)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": {
      "type": "group",
      "match_policy": "union",
      "dicts": [
        { "type": "ocd2", "file": "STPhrases.ocd2" },
        { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
      ]
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              { "type": "ocd2", "file": "STPhrases.ocd2" },
              { "type": "ocd2", "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2" }
            ]
          },
          { "type": "ocd2", "file": "STCharacters.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TWPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariantsPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariants.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/t2hk.json" to """{
  "name": "Traditional Chinese (OpenCC Standard) to Traditional Chinese (Hong Kong variant)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "HKVariantsPhrases.ocd2" },
          { "type": "ocd2", "file": "HKVariants.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/t2jp.json" to """{
  "name": "Old Japanese Kanji (Kyūjitai) to New Japanese Kanji (Shinjitai)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    { "dict": { "type": "ocd2", "file": "JPShinjitaiCharactersRev.ocd2" } }
  ]
}
""",
        "data/config/t2s.json" to """{
  "name": "Traditional Chinese (OpenCC Standard) to Simplified Chinese",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TSPhrases.ocd2" },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          { "type": "ocd2", "file": "TSCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/t2tw.json" to """{
  "name": "Traditional Chinese (OpenCC Standard) to Traditional Chinese (Taiwan Standard)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TWVariantsPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariants.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/tw2s.json" to """{
  "name": "Traditional Chinese (Taiwan Standard) to Simplified Chinese",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": { "type": "ocd2", "file": "TSPhrases.ocd2" }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TWVariantsRevPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariantsRev.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TSPhrases.ocd2" },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          { "type": "ocd2", "file": "TSCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/tw2sp.json" to """{
  "name": "Traditional Chinese (Taiwan Standard) to Simplified Chinese (Mainland China Phrases)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "segmentation": {
    "type": "mmseg",
    "dict": { "type": "ocd2", "file": "TSPhrases.ocd2" }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TWPhrasesRev.ocd2" },
          { "type": "ocd2", "file": "TWVariantsRevPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariantsRev.ocd2" }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TSPhrases.ocd2" },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          { "type": "ocd2", "file": "TSCharacters.ocd2" }
        ]
      }
    }
  ]
}
""",
        "data/config/tw2t.json" to """{
  "name": "Traditional Chinese (Taiwan Standard) to Traditional Chinese (OpenCC Standard)",
  "normalization": [
    { "dict": { "type": "ocd2", "file": "CJK_Compatibility_Ideographs.ocd2" } }
  ],
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          { "type": "ocd2", "file": "TWVariantsRevPhrases.ocd2" },
          { "type": "ocd2", "file": "TWVariantsRev.ocd2" }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/hk2sp_jieba.json" to """{
  "name": "Traditional Chinese (Hong Kong variant) to Simplified Chinese (with phrases, Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "HKPhrasesRev.ocd2"
          },
          {
            "type": "ocd2",
            "file": "HKVariantsRevPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "HKVariantsRev.ocd2"
          }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "TSPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          {
            "type": "ocd2",
            "file": "TSCharacters.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/s2hk_jieba.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Hong Kong variant, Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              {
                "type": "ocd2",
                "file": "STPhrases.ocd2"
              },
              {
                "type": "ocd2",
                "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2"
              }
            ]
          },
          {
            "type": "ocd2",
            "file": "STCharacters.ocd2"
          }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "HKVariantsPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "HKVariants.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/s2hkp_jieba.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Hong Kong variant, with phrases, Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              {
                "type": "ocd2",
                "file": "STPhrases.ocd2"
              },
              {
                "type": "ocd2",
                "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2"
              }
            ]
          },
          {
            "type": "ocd2",
            "file": "STCharacters.ocd2"
          }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "HKPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "HKVariantsPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "HKVariants.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/s2t_jieba.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              {
                "type": "ocd2",
                "file": "STPhrases.ocd2"
              },
              {
                "type": "ocd2",
                "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2"
              }
            ]
          },
          {
            "type": "ocd2",
            "file": "STCharacters.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/s2tw_jieba.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Taiwan standard, Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              {
                "type": "ocd2",
                "file": "STPhrases.ocd2"
              },
              {
                "type": "ocd2",
                "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2"
              }
            ]
          },
          {
            "type": "ocd2",
            "file": "STCharacters.ocd2"
          }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "TWVariantsPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TWVariants.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/s2twp_jieba.json" to """{
  "name": "Simplified Chinese to Traditional Chinese (Taiwan standard, with phrases, Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "group",
            "match_policy": "union",
            "dicts": [
              {
                "type": "ocd2",
                "file": "STPhrases.ocd2"
              },
              {
                "type": "ocd2",
                "file": "STPhrases_GeneratedFromRegionalPhrases.ocd2"
              }
            ]
          },
          {
            "type": "ocd2",
            "file": "STCharacters.ocd2"
          }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "TWPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TWVariantsPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TWVariants.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "plugins/jieba/data/config/tw2sp_jieba.json" to """{
  "name": "Traditional Chinese (Taiwan standard) to Simplified Chinese (with phrases, Jieba Segmentation - Experimental)",
  "normalization": [
    {
      "dict": {
        "type": "ocd2",
        "file": "CJK_Compatibility_Ideographs.ocd2"
      }
    }
  ],
  "segmentation": {
    "type": "jieba",
    "resources": {
      "dict_path": "jieba_dict/jieba_merged.ocd2",
      "model_path": "jieba_dict/hmm_model.utf8"
    }
  },
  "conversion_chain": [
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "TWPhrasesRev.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TWVariantsRevPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TWVariantsRev.ocd2"
          }
        ]
      }
    },
    {
      "dict": {
        "type": "group",
        "match_policy": "short_circuit",
        "dicts": [
          {
            "type": "ocd2",
            "file": "TSPhrases.ocd2"
          },
          {
            "type": "ocd2",
            "file": "TSCharactersExt.ocd2",
            "may_output_tofu": true
          },
          {
            "type": "ocd2",
            "file": "TSCharacters.ocd2"
          }
        ]
      }
    }
  ]
}
""",
        "test/config_test/config_test.json" to """{
  "name": "Configuration Test",
  "segmentation": {
    "type": "mmseg",
    "dict": {
      "type": "text",
      "file": "config_test_phrases.txt"
    }
  },
  "conversion_chain": [{
    "dict": {
      "type": "group",
      "match_policy": "short_circuit",
      "dicts": [{
        "type": "text",
        "file": "config_test_phrases.txt"
      }, {
        "type": "text",
        "file": "config_test_characters.txt"
      }]
    }
  }]
}
""",
        "test/config_test/config_test_nested_group.json" to """{
  "name": "Configuration Test (Nested Groups)",
  "segmentation": {
    "type": "mmseg",
    "dict": {
      "type": "group",
      "match_policy": "short_circuit",
      "dicts": [
        {
          "type": "inline",
          "entries": {
            "燕燕于飞": "燕燕于飞"
          }
        },
        {
          "type": "text",
          "file": "config_test_phrases.txt"
        }
      ]
    }
  },
  "conversion_chain": [{
    "dict": {
      "type": "group",
      "match_policy": "short_circuit",
      "dicts": [
        {
          "type": "group",
          "match_policy": "short_circuit",
          "dicts": [
            {
              "type": "text",
              "file": "config_test_phrases.txt"
            },
            {
              "type": "text",
              "file": "config_test_characters.txt"
            }
          ]
        },
        {
          "type": "inline",
          "entries": {
            "远": "遠"
          }
        }
      ]
    }
  }]
}
"""
    )
}
