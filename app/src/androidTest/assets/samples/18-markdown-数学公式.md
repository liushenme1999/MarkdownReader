# 数学公式渲染测试

行内公式 $E = mc^2$、$\frac{a+b}{c}$、$\sqrt{x^2+y^2}$、$e^{i\pi}+1=0$。

希腊字母：$\alpha\ \beta\ \gamma\ \Delta\ \Omega\ \theta\ \lambda\ \mu\ \pi\ \sigma\ \varphi$

块级公式：

$$
\int_{-\infty}^{\infty} e^{-x^{2}}\,\mathrm{d}x = \sqrt{\pi}
$$

$$
\frac{\partial^{2} u}{\partial t^{2}} = c^{2}\nabla^{2} u
$$

矩阵：

$$
A = \begin{bmatrix}
1 & 2 & 3 \\
4 & 5 & 6 \\
7 & 8 & 9
\end{bmatrix},\qquad
\det(A) = 0
$$

分段函数：

$$
f(x) = \begin{cases}
x^{2}, & x \ge 0 \\
-x, & x < 0
\end{cases}
$$

多行对齐：

$$
\begin{aligned}
(a+b)^{2} &= a^{2} + 2ab + b^{2} \\
(a-b)^{2} &= a^{2} - 2ab + b^{2}
\end{aligned}
$$

求和与极限：

$$
\sum_{k=1}^{n} k = \frac{n(n+1)}{2},\qquad
\lim_{x \to 0} \frac{\sin x}{x} = 1
$$

> 关注点：块级公式是否居中、上下留白是否合适、长公式是否横向溢出、深色主题下的颜色是否可读。
