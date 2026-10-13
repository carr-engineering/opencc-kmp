package io.github.opencckmp.jieba
import io.github.opencckmp.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Exact plugin integration outputs with registered Kotlin factories and host resources. */
class UpstreamJiebaLiteralTest {
    private val root = File(System.getProperty("opencc.projectRoot") ?: error("Run upstreamJiebaConformance"))
    private val bundle = File(root, ".cache/conformance/jieba/opencc")
    private fun engine(name: String, directory: File = bundle): Converter = Config(ResourceProvider { resource ->
        val file = File(directory, resource)
        if (!file.isFile) throw FileNotFound(resource)
        file.readBytes()
    }, segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory())).newFromFile("$name.json", ConfigLoadOptions(false))
    @Test fun convertFromJsonCases() {
        val converters = mutableMapOf<String, Converter>()
        for ((config, input, sourceExpected) in cases) {
            val process = ProcessBuilder(File(root, ".cache/oracle/src/tools/opencc").absolutePath,
                "-c", File(bundle, "$config.json").absolutePath, "--path", bundle.absolutePath)
                .apply { environment()["OPENCC_SEGMENTATION_PLUGIN_PATH"] = File(root, ".cache/oracle/plugins/jieba").absolutePath }.start()
            process.outputStream.use { it.write(input.encodeToByteArray()) }
            val native = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), error)
            if (config == "tw2sp" && input == "奶茶店慰藉著旅者的味蕾") {
                // ver.1.4.2 source fixture predates its phrase dictionary correction.
                // Record both; observable native behavior is the compatibility target.
                assertEquals("奶茶店慰借着旅者的味蕾", sourceExpected)
                assertEquals("奶茶店慰藉着旅者的味蕾", native)
            } else assertEquals(sourceExpected, native, "native source fixture / $config / $input")
            assertEquals(native, converters.getOrPut(config) { engine(config) }.convert(input), "$config / $input")
        }
    }
    @Test fun convertWithMergedOcd2JiebaDictionary() {
        assertEquals("生活著名為正敏的少女", engine("s2twp_jieba").convert("生活着名为正敏的少女"))
    }
    @Test fun convertFromUnicodePluginPath() {
        val temp = Files.createTempDirectory("opencc-中文路径-")
        val unicodeResources = temp.resolve("資源😀")
        try {
            Files.createSymbolicLink(unicodeResources, bundle.toPath())
            assertEquals("生活著名為正敏的少女", engine("s2twp_jieba", unicodeResources.toFile()).convert("生活着名为正敏的少女"))
        } finally { Files.deleteIfExists(unicodeResources); Files.deleteIfExists(temp) }
    }
    private val cases = listOf(
        Triple("s2twp", "勇敢的士兵", "勇敢計程車兵"),
        Triple("s2twp_jieba", "勇敢的士兵", "勇敢的士兵"),
        Triple("tw2sp", "生活著名為正敏的少女", "生活著名为正敏的少女"),
        Triple("tw2sp_jieba", "生活著名為正敏的少女", "生活著名为正敏的少女"),
        Triple("tw2sp_jieba", "㑮", "㑮"),
        Triple("hk2sp_jieba", "㑮", "㑮"),
        Triple("s2twp", "一个中学生，一个中等身材的人", "一箇中學生，一箇中等身材的人"),
        Triple("s2twp_jieba", "一个中学生，一个中等身材的人", "一個中學生，一個中等身材的人"),
        Triple("tw2sp", "一個中學生，一個中等身材的人", "一个中学生，一个中等身材的人"),
        Triple("tw2sp_jieba", "一個中學生，一個中等身材的人", "一个中学生，一个中等身材的人"),
        Triple("s2twp", "我来到北京清华大学学习自然语言处理", "我來到北京清華大學學習自然語言處理"),
        Triple("s2twp_jieba", "我来到北京清华大学学习自然语言处理", "我來到北京清華大學學習自然語言處理"),
        Triple("tw2sp", "我來到北京清華大學學習自然語言處理", "我来到北京清华大学学习自然语言处理"),
        Triple("tw2sp_jieba", "我來到北京清華大學學習自然語言處理", "我来到北京清华大学学习自然语言处理"),
        Triple("s2t_jieba", "这个软件的用户体验很好", "這個軟件的用戶體驗很好"),
        Triple("s2tw_jieba", "这个软件的用户体验很好", "這個軟件的用戶體驗很好"),
        Triple("s2hk_jieba", "这个软件的用户体验很好", "這個軟件的用户體驗很好"),
        Triple("s2twp", "这个软件的用户体验很好", "這個軟體的使用者體驗很好"),
        Triple("s2twp_jieba", "这个软件的用户体验很好", "這個軟體的使用者體驗很好"),
        Triple("tw2sp", "這個軟體的功能很強大", "这个软件的功能很强大"),
        Triple("tw2sp_jieba", "這個軟體的功能很強大", "这个软件的功能很强大"),
        Triple("s2twp", "OpenCC是一个开源的中文简繁转换项目", "OpenCC是一個開源的中文簡繁轉換專案"),
        Triple("s2twp_jieba", "OpenCC是一个开源的中文简繁转换项目", "OpenCC是一個開源的中文簡繁轉換專案"),
        Triple("s2twp", "他说的确实在理", "他說的確實在理"),
        Triple("s2twp_jieba", "他说的确实在理", "他說的確實在理"),
        Triple("s2twp", "研究生命科学的重要性", "研究生命科學的重要性"),
        Triple("s2twp_jieba", "研究生命科学的重要性", "研究生命科學的重要性"),
        Triple("s2twp", "这个软体的功能很强大", "這個軟體的功能很強大"),
        Triple("s2twp_jieba", "这个软体的功能很强大", "這個軟體的功能很強大"),
        Triple("s2twp", "人工智能技术在医疗保健领域的应用", "人工智慧技術在醫療保健領域的應用"),
        Triple("s2twp_jieba", "人工智能技术在医疗保健领域的应用", "人工智慧技術在醫療保健領域的應用"),
        Triple("s2twp", "温故而知新，可以为师矣", "溫故而知新，可以為師矣"),
        Triple("s2twp_jieba", "温故而知新，可以为师矣", "溫故而知新，可以為師矣"),
        Triple("tw2sp", "奶茶店慰藉著旅者的味蕾", "奶茶店慰借着旅者的味蕾"),
        Triple("tw2sp_jieba", "奶茶店慰藉著旅者的味蕾", "奶茶店慰藉着旅者的味蕾"),
        Triple("s2twp", "城堡的士兵已经出发", "城堡計程車兵已經出發"),
        Triple("s2twp_jieba", "城堡的士兵已经出发", "城堡的士兵已經出發"),
        Triple("s2tw_jieba", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
        Triple("s2hk_jieba", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
        Triple("s2twp_jieba", "牛骥同皁 牛骥同皂 牛骥同一皁 牛骥同一皂", "牛驥同皁 牛驥同皁 牛驥同一皁 牛驥同一皁"),
        Triple("s2twp", "拥有一百年历史", "擁有一百年曆史"),
        Triple("s2twp_jieba", "拥有一百年历史", "擁有一百年歷史"),
        Triple("s2t_jieba", "刚一出火车站，就直奔剧场看了一出单口喜剧，演员演的有一点出戏，一出剧院[sic]，就发现一出租车停在门口招揽生意", "剛一出火車站，就直奔劇場看了一齣單口喜劇，演員演的有一點出戲，一齣劇院[sic]，就發現一出租車停在門口招攬生意")
    )
}
