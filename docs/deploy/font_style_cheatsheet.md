# 模板字体格式速查（python-docx 填充时必须遵守）

## 样式级定义（不修改，复用模板已有样式）

| 样式名 | 字体 | 字号 | 粗体 | 对齐 | 行距 | 段前 | 段后 | 用途 |
|--------|------|------|------|------|------|------|------|------|
| 主题 | 宋体 (eastAsia/ascii/hAnsi 均为宋体) | 330200 EMU (26pt) | — | CENTER | 1.5 | — | — | 封面大标题 |
| Heading 1 | (继承) | 279400 EMU (22pt) | True | — | 2.41 | 215900 | 209550 | 一级标题 |
| Heading 2 | (继承) | 203200 EMU (16pt) | True | — | 1.73 | 165100 | 165100 | 二级标题 |
| Heading 3 | (继承) | 203200 EMU (16pt) | True | — | 1.73 | 165100 | 165100 | 三级标题 |
| Normal | (继承) | — (默认约10.5pt) | — | JUSTIFY | — | — | — | 正文 |
| List Paragraph | (继承) | — | — | — | — | — | — | 变更原因列举（有首行缩进266700） |
| Table Grid | 表格样式 | — | — | — | — | — | — | 所有表格 |

## Run 级覆盖（所有正文 run 都显式设置了 font.name）

**全局规则：** 模板中 **每一个** 文本 run 都显式设置了 `font.name = 'Times New Roman'`

| 段落类型 | run.font.name | run.font.size | run.font.bold | 说明 |
|----------|---------------|---------------|---------------|------|
| 封面标题行1 "XXX项目" | Times New Roman | 继承样式(26pt) | True | run 显式 bold=True |
| 封面标题行2 "安装维护手册" | Times New Roman | 279400 (22pt) | True | run 显式 size+ bold |
| 封面状态表格单元格 | Times New Roman | 114300 (9pt) | None | 表格0 专用 |
| 其他表格单元格 | Times New Roman | 继承(默认) | None/True(表头) | 表头 bold=True |
| "变更履历" 段落(P4) | Times New Roman | 203200 (16pt) | None | 特殊大字号正文 |
| 所有 Heading 1/2/3 | Times New Roman | 继承样式 | 继承样式 | — |
| 所有 Normal 正文 | Times New Roman | 继承(默认) | None | — |
| "第X步："中的 "第X步" | Times New Roman | 继承(默认) | True | 只有数字部分加粗 |
| "第X步："中的 "：" | Times New Roman | 继承(默认) | None | 冒号不加粗 |
| "系统部署信息汇总表" | Times New Roman | 继承(默认) | True | P60, Normal+bold |
| 签字栏空行 | Times New Roman | 继承(默认) | None | "\n\n\n\n日期：" 4个空段落 |

## 表格结构（不变）

- 表格0：5行×3列（封面信息）
- 表格1：2行×3列（签字）
- 表格2：5行×8列（变更履历）
- 表格3：5行×2列（硬件环境）
- 表格4：6行×3列（部署信息汇总）

## 页脚

- 节0 页脚：已包含机密声明（需替换 XXX有限公司 → WHUSingularity 项目组）
- 节1 页脚：已包含公司名（需替换）

## 填充策略

1. **不新建段落/表格**，在原模板上替换 run.text 和部分 cell.text
2. **样式不动**，只改文本内容
3. 需要扩展的章节（如安装步骤较多），在原段落基础上增加内容
4. 表格数据按单元格逐格替换
5. 页脚文本整体替换 "XXX" 相关内容
