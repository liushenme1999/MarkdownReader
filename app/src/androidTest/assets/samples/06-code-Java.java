package com.example.reader;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码阅读测试（Java）：注解、泛型、内部类与长方法链。
 */
public final class ReadingProgress {

    private final List<Checkpoint> checkpoints = new ArrayList<>();

    public void record(int chapterIndex, int offset, float percent) {
        checkpoints.add(new Checkpoint(chapterIndex, offset, percent));
        if (checkpoints.size() > 200) {
            checkpoints.remove(0);
        }
    }

    public Checkpoint latest() {
        return checkpoints.isEmpty() ? Checkpoint.EMPTY : checkpoints.get(checkpoints.size() - 1);
    }

    // 超长行：这一行用来验证横向滚动与自动换行两种模式下的显示效果，包含中英文混排与标点符号，确保阅读器的代码块宽度计算、行号对齐、缩进与选中复制都能正常工作。intentionally-long-line-for-horizontal-scrolling-check.
    public static String describe(List<Checkpoint> list) {
        return list.stream()
                .map(c -> "chapter=" + c.chapterIndex + ", offset=" + c.offset + ", percent=" + c.percent)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("empty");
    }

    public static final class Checkpoint {
        static final Checkpoint EMPTY = new Checkpoint(-1, -1, 0f);
        final int chapterIndex;
        final int offset;
        final float percent;

        Checkpoint(int chapterIndex, int offset, float percent) {
            this.chapterIndex = chapterIndex;
            this.offset = offset;
            this.percent = percent;
        }
    }
}
