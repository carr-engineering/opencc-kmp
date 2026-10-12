package io.github.opencckmp.consumer

import io.github.opencckmp.*
import io.github.opencckmp.jieba.*
import kotlin.test.assertEquals

// Native answers from the pinned Jieba oracle, with includeTofuRiskDictionaries=true.
private val jiebaAnswers = listOf(
    Triple("hk2sp_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("hk2sp_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛骥同皂 牛骥同皂 牛骥同一皂 牛骥同一皂"),
    Triple("hk2sp_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
    Triple("s2hk_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("s2hk_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
    Triple("s2hk_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
    Triple("s2hkp_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("s2hkp_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
    Triple("s2hkp_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
    Triple("s2t_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("s2t_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
    Triple("s2t_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
    Triple("s2tw_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("s2tw_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
    Triple("s2tw_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
    Triple("s2twp_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("s2twp_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
    Triple("s2twp_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
    Triple("tw2sp_jieba.json", "勇敢的士兵", "勇敢的士兵"),
    Triple("tw2sp_jieba.json", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛骥同皂 牛骥同皂 牛骥同一皂 牛骥同一皂"),
    Triple("tw2sp_jieba.json", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文", "abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文"),
)

internal fun verifyPublishedJieba(provider: ResourceProvider) {
    if (fixtureProfile != "full") return
    val first = SimpleConverter("s2twp_jieba", provider, segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory()))
    val segmentation = checkNotNull(first.segmentation)
    val shared = SegmentationFactory { _, _ -> segmentation }
    for ((name, cases) in jiebaAnswers.groupBy { it.first }) {
        val converter = Config(provider, segmentationFactories = mapOf("jieba" to shared)).newFromFile(name)
        for ((_, input, expected) in cases) assertEquals(expected, converter.convert(input), name)
    }
}

internal suspend fun verifyPublishedJieba(provider: AsyncResourceProvider) {
    if (fixtureProfile != "full") return
    val first = prepareConfig("s2twp_jieba", provider, segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory()))
    val segmentation = checkNotNull(first.segmentation)
    val shared = object : SegmentationFactory {
        override fun create(properties: SegmentationProperties, resources: ResourceProvider): Segmentation = segmentation
        override fun resourcesToPrepare(properties: SegmentationProperties, declaredResources: List<String>): List<String> = emptyList()
    }
    for ((name, cases) in jiebaAnswers.groupBy { it.first }) {
        val converter = prepareConfig(name, provider, segmentationFactories = mapOf("jieba" to shared))
        for ((_, input, expected) in cases) assertEquals(expected, converter.convert(input), name)
    }
}
