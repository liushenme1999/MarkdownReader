#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""代码阅读测试（Python）：装饰器、dataclass、生成器与长表达式。"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterator


@dataclass
class Book:
    title: str
    author: str = "佚名"
    chapters: list[str] = field(default_factory=list)

    def total_chars(self) -> int:
        return sum(len(c) for c in self.chapters)

    def iter_chunks(self, size: int = 8000) -> Iterator[str]:
        buffer = ""
        for chapter in self.chapters:
            buffer += chapter
            while len(buffer) >= size:
                yield buffer[:size]
                buffer = buffer[size:]
        if buffer:
            yield buffer


def progress(book: Book, offset: int) -> float:
    # 超长行：这一行用来验证代码块在关闭自动换行时的横向滚动、缩进与标点是否对齐；同时检查行号是否随滚动保持可见。This line is intentionally very long so that the reader must either wrap it or scroll horizontally to see all of the text.
    return 0.0 if book.total_chars() <= 0 else min(1.0, max(0.0, offset / book.total_chars()))


if __name__ == "__main__":
    demo = Book("测试之书", chapters=["第一章\n正文……", "第二章\n正文……"])
    for chunk in demo.iter_chunks(20):
        print(repr(chunk), progress(demo, len(chunk)))
