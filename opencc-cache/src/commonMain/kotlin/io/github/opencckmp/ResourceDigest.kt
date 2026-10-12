package io.github.opencckmp

/** Portable SHA-256 used for resource identity and optional host-delivery verification. */
object ResourceDigest {
    fun sha256(bytes: ByteArray): String {
        val hash = intArrayOf(0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
            0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19)
        val words = IntArray(64)
        val blocks = (bytes.size.toLong() + 9 + 63) / 64
        val bitLength = bytes.size.toLong() * 8
        val lengthStart = blocks * 64 - 8
        for (block in 0 until blocks) {
            for (word in 0 until 16) {
                var value = 0
                for (byte in 0 until 4) {
                    val position = block * 64 + word * 4 + byte
                    val octet = when {
                        position < bytes.size -> bytes[position.toInt()].toInt() and 255
                        position == bytes.size.toLong() -> 128
                        position >= lengthStart -> (bitLength ushr ((7 - (position - lengthStart).toInt()) * 8)).toInt() and 255
                        else -> 0
                    }
                    value = (value shl 8) or octet
                }
                words[word] = value
            }
            for (i in 16 until 64) {
                val x = words[i - 15]
                val y = words[i - 2]
                val s0 = x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3)
                val s1 = y.rotateRight(17) xor y.rotateRight(19) xor (y ushr 10)
                words[i] = words[i - 16] + s0 + words[i - 7] + s1
            }
            var a = hash[0]; var b = hash[1]; var c = hash[2]; var d = hash[3]
            var e = hash[4]; var f = hash[5]; var g = hash[6]; var h = hash[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choice = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + choice + constants[i] + words[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + majority
                h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            hash[0] += a; hash[1] += b; hash[2] += c; hash[3] += d
            hash[4] += e; hash[5] += f; hash[6] += g; hash[7] += h
        }
        return buildString(64) {
            for (value in hash) for (shift in 28 downTo 0 step 4) append("0123456789abcdef"[(value ushr shift) and 15])
        }
    }

    private val constants = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
}
