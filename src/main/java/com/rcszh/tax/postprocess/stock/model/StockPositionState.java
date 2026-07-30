package com.rcszh.tax.postprocess.stock.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 持仓台账（加权平均单价 / 移动平均）。
 */
@Data
public class StockPositionState {
    /**
     * 当前持仓数量（股）。
     */
    private BigDecimal positionQty = BigDecimal.ZERO;

    /**
     * 当前持仓总成本
     */
    private BigDecimal positionCostTotal = BigDecimal.ZERO;

    /**
     * 当前加权平均单价 = positionCostTotal / positionQty。
     * <p>
     * 注意：需要高精度保存；展示时再四舍五入。
     */
    private BigDecimal avgUnitCost = BigDecimal.ZERO;

    /**
     * 依据当前持仓数量与总成本，重新计算加权平均单价。
     */
    public void recalcAvg() {
        if (positionQty == null || positionQty.compareTo(BigDecimal.ZERO) <= 0) {
            avgUnitCost = BigDecimal.ZERO;
            return;
        }
        if (positionCostTotal == null) {
            positionCostTotal = BigDecimal.ZERO;
        }
        avgUnitCost = positionCostTotal.divide(positionQty, 18, RoundingMode.HALF_UP);
    }
}
