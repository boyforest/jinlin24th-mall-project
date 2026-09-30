/**
 * 库存页「单仓模式」的可见性判定（纯逻辑，便于单测）。
 *
 * 为什么抽出来：这个判定的正确性直接关系到"库存被写进哪个仓"。
 * 判错的后果是静默的数据错误——比如把多仓环境误判为单仓，界面隐藏仓库选择，
 * 运营以为在改 A 仓，实际改的是 B 仓。
 *
 * 因此遵循**失败即保守**：只有后端明确返回 `singleWarehouse === true` 才进入单仓模式；
 * 字段缺失、类型异常（如字符串 "true"、数字 1）一律按多仓处理，继续显示显式仓库选择。
 */

/**
 * 是否为单仓模式。
 *
 * @param {object|null|undefined} mode 后端 `/admin/inventory/mode` 的返回
 * @returns {boolean} 严格等于 true 才返回 true
 */
export function isSingleWarehouseMode(mode) {
  return mode?.singleWarehouse === true
}

/**
 * 计算库存页各处的仓库相关元素是否可见。
 *
 * @param {object|null|undefined} mode
 * @returns {{singleWarehouse: boolean, showWarehouseFilter: boolean, showWarehouseColumn: boolean,
 *            showWarehouseFormField: boolean, showWarehouseDetailRow: boolean}}
 */
export function inventoryWarehouseVisibility(mode) {
  const singleWarehouse = isSingleWarehouseMode(mode)
  return {
    singleWarehouse,
    showWarehouseFilter: !singleWarehouse,
    showWarehouseColumn: !singleWarehouse,
    showWarehouseFormField: !singleWarehouse,
    showWarehouseDetailRow: !singleWarehouse
  }
}
