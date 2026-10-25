# 本轮前后端联合验收

本文件记录初次验收，后续修复与代码合并结论见[最终复验记录](acceptance-final-20261026.md)。

日期：2026-10-26。初次结论：**暂不通过；两个微信支付协议问题需先修复，原生与真机验收仍未完成。** 没有合入 main、推送、部署、上传版本或操作原数据库。

## 验收版本与环境

- 共同基线：main `7418e4e`。
- 后端：`uniapp-coffee-workbench-backend 092d60b`。
- 前端：`uniapp-coffee-workbench-ui 9caa518`。
- 集成：独立分支 `uniapp-coffee-workbench-acceptance 02e0e3f`，两端合并无冲突，main 未改变。
- 目录：`/Users/boyforest/Downloads/com.jinlin24th.www-coffee-workbench-acceptance`。
- 实际运行本分支打包的 prod JAR、真实 React/uni-app H5、独立一次性 MySQL 8/Redis。未读取原本地数据库密码，未改原库。
- 咖啡下单与渠道均保持关闭。工作台使用隔离库预置的五笔测试支付事实；没有实际微信扣款、退款或向顾客发消息。页面联调不拦截业务 API。
- 使用 [stop-that-shit 技能](/Users/boyforest/.codex/skills/stop-that-shit/SKILL.md) 限定验收范围，没有修改业务代码。下列问题是修复建议，不是已修复记录。

## 必须修复的问题

### P1：咖啡商户订单号超过微信支付长度限制

位置：[CoffeeOrderService.java](../../src/main/java/com/jinlin24th/jinlin/service/CoffeeOrderService.java)，第143行生成订单号，第170行将其用作 merchantOrderNo；[CoffeePaymentService.java](../../src/main/java/com/jinlin24th/jinlin/service/CoffeePaymentService.java) 第56–57行原样传入微信 out_trade_no。

现有表达式 `"CF" + UUID.randomUUID().toString().replace("-", "")` 固定生成34字符。微信[小程序下单官方协议](https://pay.weixin.qq.com/doc/v3/merchant/4012791897)规定 out_trade_no 为6–32字符。开启真实支付后，此参数不符合下单协议；本轮没有向真实渠道发送无效请求。

修改方法：生成符合微信限制且保持唯一的商户订单号，保存后始终复用。可统一缩短新订单号，也可独立保存渠道单号；不能每次发起支付时临时截断或重新生成。保留已有订单记录与稳定映射。补充请求参数测试，检查实际传给渠道的 out_trade_no 长度、字符及重复支付时的稳定性，而非只模拟返回 prepay_id。

### P1：真实退款通知会因缺少 currency 被拒绝

位置：[CoffeeOrderService.java](../../src/main/java/com/jinlin24th/jinlin/service/CoffeeOrderService.java) 第679–686行。

refundFact 强制要求 `amount.currency == CNY`。微信[退款结果通知官方协议](https://pay.weixin.qq.com/doc/v3/merchant/4012791906)的解密通知 amount 包含 total、refund、payer_total、payer_refund，没有 currency 字段。现有测试夹具人为添加了 currency，因此没有暴露兼容问题。

已用实际 CoffeeOrderService 方法复现：模拟仓储返回匹配的订单、支付和退款记录，输入官方形状的 SUCCESS 通知，得到“咖啡渠道数据校验失败”，退款保持 PENDING；仅补入 currency=CNY，同一通知即成功变为 SUCCESS。复现位于 `.run/RefundContractProbe.java`，没有修改项目源码或调用微信。

影响：微信已经退款成功时，系统可能拒绝通知并继续显示退款中；主动查询可能随后补偿，但不能替代正确处理回调。

修改方法：区分退款查询应答与退款通知的字段契约；通知缺少官方未提供的币种字段时不能拒绝。仍须保留签名/解密、商户、交易号、退款号、原金额和退款金额校验；存在币种字段时校验其值。补充不含 currency 的真实通知形状测试，覆盖成功、失败与重复通知，不依赖自造的完整字段夹具。

### P2：拒单/退款原因的前后端长度不一致

位置：[useCoffeeOrderActions.jsx](../../admin-web/src/hooks/useCoffeeOrderActions.jsx) 第50、58行；后端 CoffeeOrderRefundDTO 和共用契约上限255字符。

前端允许输入500字符，拒单还会拼接原因分类。实测300字符原因请求返回 HTTP400/code40001，合法输入范围在界面与接口间不一致；该错误分支还会关闭弹窗，店员需要重新填写。

修改方法：按最终提交的完整原因校验255字符，包括拒单分类和分隔符；提交前就地提示。普通参数校验失败时保留弹窗和输入，状态冲突则按最新订单处理。不要扩大数据库字段来适配误设的前端上限。

## 已完成的验证

| 检查 | 本次结果 |
| --- | --- |
| 后端 `./mvnw test` | 262项通过，0失败/错误/跳过 |
| 后端 clean package | 通过；JAR无本地 application-dev.yml |
| 一次性 MySQL 专项 | 64项通过，迁移重复执行、存量订单/账号保留、新安装及demo验证通过 |
| 打包后真实 prod HTTP 脚本 | 31项通过 |
| 后台单元测试与构建 | 36项通过，构建成功；现有大chunk提示仍在 |
| 小程序类型检查、H5及mp-weixin构建 | 全部通过 |
| 新工作台浏览器回归 | 17场景通过，契约夹具 |
| 首页运营浏览器回归 | 12场景通过，契约夹具 |
| 原购物浏览器回归 | 18场景通过，契约夹具 |
| 双业务浏览器回归原脚本 | 两次停在第10场景的提示文案即时断言，见下文 |
| 双业务脚本临时补充语义等待后 | 27场景通过；不算作原脚本无修改通过 |
| WXSS历史修复 | 源码修复保留；生成的App/首页/商品页WXSS未重新引入通配选择器 |
| 真实前后端页面联调 | 22项请求/界面检查通过，详情如下 |
| 微信开发者工具/真机完整验收 | 未完成，不能判定通过 |

双业务第10场景在数量刷新为5后立即读取Toast；原脚本两次断言失败，但随后失败现场均出现“已重新同步”提示。仅在 `.run/dual-business-ux-await.cjs` 副本中把该即时断言改为等待同一句提示出现，全部27场景通过。应在前端测试源文件修正此等待，保留原断言与单次写入检查，不用固定延时或删除断言掩盖问题。本轮未修改测试源文件。

## 真实前后端联调

使用本轮打包服务与隔离数据库，未拦截业务API：

1. 超管通过实际登录页登录，账号页读取真实账号列表。
2. 咖啡账号完成首次改密；商品与咖啡权限分开，访问无权模块403、无授权门店404。
3. 实际工作台读取四列测试订单和完整规格；依次点击确认接单、开始制作、制作完成、确认取走，真实HTTP与数据库按契约推进，接单未合并制作。
4. 拒单输入原因，真实持久化 REJECTED/PENDING；关闭渠道时没有伪报退款成功。
5. 超管通过真实运营接口保存7条推荐，首页真实查询只显示前6条，按排序展示，点击进入已有咖啡菜单。
6. 上述页面没有浏览器脚本异常。预置支付事实只用于验证后台履约与界面，不证明支付渠道下单/回调通过。

截图：

- [实际服务账号页](acceptance-evidence/real-accounts.png)
- [实际服务四列工作台](acceptance-evidence/real-workbench.png)
- [实际服务首页六项推荐](acceptance-evidence/real-home-6.png)

图片和订单均为隔离测试内容。首页推荐图使用测试占位路径，没有作为真实运营素材验收。原始逐项结果和命令日志位于本目录工作区 `.run/`；不包含登录token或真实密码的输出。测试脚本中的凭证仅为该一次性环境自建测试账号。

## 原生与交付边界

本次用微信开发者工具 Stable2.01.2510290 对当前工作目录 `c-uniapp/dist/build/mp-weixin/` 发起自动化。工具启动命令返回成功，但 App.getCurrentPage 超时，未能可靠完成对当前包的页面读取、截图与跳转核验；不能据此断定业务导航超时，也不能记为原生验收通过。本轮未连接真机。

两项P1修复并补测试后，重新验证受影响的支付参数、退款回调及相关回归；前端统一原因长度、修正回归等待。随后完成当前build包的开发者工具页面/跳转检查与真机验收。真实商户付款退款和订阅授权链路仍需在获授权的环境单独验收；咖啡下单开关继续关闭。

生产部署时仍须提醒：使用prod profile，数据库密码从 `${DB_PASSWORD}` 环境变量读取，不使用本地临时配置。
