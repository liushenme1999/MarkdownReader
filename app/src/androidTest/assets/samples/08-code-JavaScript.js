// 代码阅读测试（JavaScript）：模块、异步、可选链与长模板字符串。
const chapters = new Map();

export function registerChapter(index, title, offset) {
  chapters.set(index, { index, title, offset, chars: 0 });
  return chapters.get(index);
}

export async function loadBook(url, { signal } = {}) {
  const response = await fetch(url, { signal });
  if (!response.ok) throw new Error(`加载失败: ${response.status}`);
  const text = await response.text();
  return text.split(/\n{2,}/).filter(Boolean);
}

export const progressOf = (offset, total) => (total > 0 ? Math.min(1, Math.max(0, offset / total)) : 0);

// 下面这行故意很长：检查代码块横向滚动与自动换行两种显示模式。
export const intentionallyLongLine = "0123456789".repeat(8) + "-" + "abcdefghij".repeat(6) + "-" + "这一段是中文内容，用来确认中英文混排时宽度计算与换行位置是否正确";

export function summarize(list) {
  return list
    ?.filter((item) => item?.chars > 0)
    .map((item) => `${item.index}. ${item.title} (${item.chars} 字)`)
    .join("\n") ?? "";
}
