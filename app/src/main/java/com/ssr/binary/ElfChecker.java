package com.ssr.binary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ElfChecker {

    public static String checkElfArchitecture(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[20];
            int read = fis.read(header);
            if (read < 20) {
                return "文件太短，无法判定";
            }

            // ELF 魔数检查
            if (header[0] != 0x7f || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
                return "不是 ELF 文件";
            }

            // 解析位数
            int ei_class = header[4] & 0xFF;
            String elfClass = "未知";
            if (ei_class == 1) {
                elfClass = "32位";
            } else if (ei_class == 2) {
                elfClass = "64位";
            } else {
                elfClass = "未识别位数";
            }

            // 解析架构 Machine字段 (16-17字节)
            int e_machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);

            String arch = "未知架构";

            switch (e_machine) {
                case 0x28:
                    arch = "ARM (arm32)";
                    break;
                case 0xb7:
                    arch = "ARM64 (aarch64)";
                    break;
                case 0x03:
                    arch = "x86";
                    break;
                case 0x3e:
                    arch = "x86_64";
                    break;
                case 0x08:
                    arch = "MIPS";
                    break;
                default:
                    arch = String.format("未知机器码: 0x%04x", e_machine);
            }


            return String.format("%s ELF, %s", elfClass, arch);
        }
    }
}
