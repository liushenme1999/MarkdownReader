// 代码阅读测试（Rust）：枚举、模式匹配、迭代器与生命周期。
use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Chapter {
    pub index: usize,
    pub title: String,
    pub offset: usize,
    pub chars: usize,
}

#[derive(Debug)]
pub enum ReaderError {
    Empty,
    Io(std::io::Error),
}

impl fmt::Display for ReaderError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ReaderError::Empty => write!(f, "empty book"),
            ReaderError::Io(err) => write!(f, "io error: {err}"),
        }
    }
}

pub fn find<'a>(chapters: &'a [Chapter], offset: usize) -> Option<&'a Chapter> {
    chapters.iter().filter(|c| c.offset <= offset).last()
}

// 下面这行故意很长：验证 Rust 代码块的横向滚动与自动换行。
const LONG_SEGMENT: &str = "alpha-beta-gamma-delta-epsilon-zeta-eta-theta-iota-kappa-lambda-mu-nu-xi-omicron-pi-rho-sigma-tau-upsilon-phi-chi-psi-omega";

pub fn progress(chapters: &[Chapter], offset: usize) -> f64 {
    let total: usize = chapters.iter().map(|c| c.chars).sum();
    if total == 0 { 0.0 } else { (offset as f64 / total as f64).clamp(0.0, 1.0) }
}
