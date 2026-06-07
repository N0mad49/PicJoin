# PinPhotograph 图片拼接编辑器

## 概述
PinPhotograph 是一款功能强大的图片拼接编辑网页应用，支持两种独特的编辑模式：条形拼接和自由位置布局。应用提供丰富的边框自定义选项，最独特的功能是高斯模糊背景和高级图层混合效果。

### 访问网址

https://n0mad49.github.io/PinPhotograph/

### 效果示意
## 条形拼接模式
<div align="center">
	<img src="https://github.com/user-attachments/assets/f1408f92-ebfb-4ffa-bd1c-b0e9b0e465ad" width="20%">
	<img src="https://github.com/user-attachments/assets/9896e5da-413b-4205-9ff7-f5cd74446c45" width="20%">
	<img src="https://github.com/user-attachments/assets/c5e5d9be-0672-4abd-b990-28eee0ddf7bd" width="20%">
</div>

## 自由位置模式
<div align="center">
	<img src="https://github.com/user-attachments/assets/603895dc-cdfe-4162-a1f3-a56a4f10d402" width="50%">
	<img src="https://github.com/user-attachments/assets/ebeadfd8-1f41-43ab-8865-c787ab4fdc08" width="50%">
</div>


## 功能特性

### 核心功能

- **双模式编辑**：
    
    - **条形拼接**：将多张图片按原比例竖向拼接，保持整齐排列
        
    - **自由位置**：在自定义画布上自由调整图片位置、大小和图层顺序
        
    
- **高级背景系统**：三种背景风格可选
    
    - **纯色背景**：20种精选莫兰迪色系及自定义颜色选择
        
    - **模糊背景**：使用已选图片生成模糊背景，采用边缘扩展技术防止黑色晕染
        
    - **自定义背景**：使用自选图片作为背景
        
    
- **智能背景混合**：在自由位置模式下支持多种混合模式（叠加、滤色、正常等）
    
- **图片编辑**：
    
    - 单张图片独立调整饱和度、曝光度、对比度

    - 图片阴影效果，可自定义颜色、透明度、大小、角度等参数
        
    
- **撤销支持**：支持撤销图片排序、删除、编辑等操作

### 细节功能
- **直观的图片管理**：
    
    - 拖拽调整图片顺序
        
    - 多选 + 批量删除功能
        
    
- **智能对齐系统**：
    
    - 图片边缘自动对齐
        
    - 中轴线吸附，带视觉辅助线

- **边缘扩展模糊技术**（条形拼接模式）：为了防止背景模糊时黑色底色渗入图片区域，采用欧几里得距离变换（EDT）将图片边缘内容向外扩展填充空白区域，再对扩展后的完整画布执行高斯模糊，确保模糊过渡自然无暗晕。

- **三层图片渲染效果**（自由位置模式）：为了防止画布底色露出，模糊背景分为三层

	- **顶层**：原始清晰图片，应用饱和度/亮度/对比度调整
   
	- **中层**：可调节模糊度的模糊层，支持多种混合模式（叠加、滤色等）
   
	- **底层**：2倍放大的环境模糊层，用于防止画布底色露出，默认带有基础模糊效果
	 
<div align="center">
	<img src="https://github.com/user-attachments/assets/9b7b45c0-347e-42e6-a424-d6e5e7b0401c" width="50%">
</div>

---

_PinPhotograph 持续更新中，欢迎反馈使用体验和建议！_
