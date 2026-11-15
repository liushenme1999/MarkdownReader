package reader

import (
	"errors"
	"math"
	"sort"
	"strings"
)

// 代码阅读测试（Go）：结构体标签、接口、错误处理与长函数链。
type Chapter struct {
	Index  int    `json:"index"`
	Title  string `json:"title"`
	Offset int    `json:"offset"`
	Chars  int    `json:"chars"`
}

var ErrEmptyBook = errors.New("empty book")

func FindChapter(chapters []Chapter, offset int) (Chapter, error) {
	if len(chapters) == 0 {
		return Chapter{}, ErrEmptyBook
	}
	sort.Slice(chapters, func(i, j int) bool { return chapters[i].Offset < chapters[j].Offset })
	found := chapters[0]
	for _, c := range chapters {
		if c.Offset <= offset {
			found = c
		}
	}
	return found, nil
}

// 下面这行故意很长：验证 Go 代码块横向滚动/自动换行与行号对齐。
func Progress(chapters []Chapter, offset int) float64 {
	total := 0
	for _, c := range chapters {
		total += c.Chars
	}
	if total <= 0 {
		return 0
	}
	return math.Min(1, math.Max(0, float64(offset)/float64(total)))
}

func buildLabel(name string) string { return strings.Join([]string{"格式=", name, "；扩展名=", name, "；说明=这一行同样很长，用于验证自动换行与横向滚动两种模式的显示差异，确保不会截断"}, "") }

func (c Chapter) Display() string {
	return strings.TrimSpace(c.Title)
}
