# 验证记录

日期：2026-10-17。Java 17，Minecraft 1.20.1，隔离 Forge 47.4.26 运行环境。

- 18 项后勤核心测试通过：队伍权限、数量范围、订单独占、释放权限、真实分批交付、同电台回装排除、未知旧货、其他司机卸货、外部消耗对账、过期与生命周期、限流、历史上限、文本清理。
- 隔离专服使用真实 Espetro `loadAmmo / unloadAmmo / loadConstruction / unloadConstruction` 方法，验证监听触发、分批交付、实际 Radio 库存和订单完成一致；同 Radio 回装不增加交付进度。
- 客户端验证数据编解码、后勤面板、发布表单与 GUI 缩放。文档截图为实际 Minecraft 界面，列表中使用测试样例数据。
- `./gradlew :core:test :forge:build` 完整构建通过，生成可安装的 Forge 生产包；CI 单独运行核心测试。

Espetro main 的全工程编译依赖本地 DragonRise 编译 jar，本工作站未具备该依赖。因此集成测试基于已有 Espetro 1.1.3-l 测试包，不宣称对当前 main 整包或所有载具进行了完整多人验收。建议先在测试服使用。
