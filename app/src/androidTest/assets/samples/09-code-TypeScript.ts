// 代码阅读测试（TypeScript）：类型、泛型、接口、联合类型与长类型声明。
export type ReaderMode = "vertical" | "paged";

export interface Chapter {
  readonly index: number;
  readonly title: string;
  readonly offset: number;
  readonly chars: number;
}

export interface ReaderOptions {
  mode: ReaderMode;
  fontSize: number;
  lineSpacing: number;
  padding: number;
}

const DEFAULT_OPTIONS: ReaderOptions = { mode: "paged", fontSize: 16, lineSpacing: 1.5, padding: 32 };

export function pickChapter<T extends Chapter>(chapters: readonly T[], offset: number): T | undefined {
  let found: T | undefined;
  for (const chapter of chapters) {
    if (chapter.offset <= offset) found = chapter;
    else break;
  }
  return found;
}

// 下面这行故意很长：验证泛型声明在横向滚动/自动换行下的显示与行号对齐。
export type VeryLongGenericType<T extends Record<string, unknown>, K extends keyof T = keyof T> = { readonly [P in K]: T[P] extends Array<infer U> ? U : T[P] extends ReadonlyArray<infer V> ? V : T[P] };

export function makeOptions(overrides: Partial<ReaderOptions> = {}): ReaderOptions {
  return { ...DEFAULT_OPTIONS, ...overrides };
}
