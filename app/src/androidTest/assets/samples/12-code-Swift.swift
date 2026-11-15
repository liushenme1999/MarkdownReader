// 代码阅读测试（Swift）：协议、泛型、属性包装与长链式调用。
import Foundation

protocol ProgressReporting {
    var totalChars: Int { get }
    func progress(at offset: Int) -> Double
}

struct Chapter: Identifiable, Hashable {
    let id: Int
    let title: String
    let offset: Int
    let chars: Int
}

struct Book: ProgressReporting {
    let title: String
    var chapters: [Chapter]

    var totalChars: Int { chapters.reduce(0) { $0 + $1.chars } }

    func progress(at offset: Int) -> Double {
        guard totalChars > 0 else { return 0 }
        return min(1, max(0, Double(offset) / Double(totalChars)))
    }
}

// 下面这行故意很长：验证 Swift 代码块在两种换行模式下的显示。
let intentionallyLongExpression = ["alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta"].map { $0.uppercased() }.joined(separator: " · ") + " · 中文内容用于确认混排宽度与换行位置"

extension Book {
    func chapter(containing offset: Int) -> Chapter? {
        chapters.last { $0.offset <= offset }
    }
}
