package com.themainthread.returns;

import java.util.List;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

public class ReturnTools {
    private final ReturnService returns;

    public ReturnTools(ReturnService returns) {
        this.returns = returns;
    }

    @Tool(name = "check_return_eligibility", description = "Calculate a return preview for an explicit order and items. "
            + "Returns eligibility details and refund cents. Does not change a browser draft or submit a return.")
    public ReturnService.Draft check(
            @ToolArg(description = "Order ID, for example ORD-1042") String orderId,
            @ToolArg(description = "Distinct order items with SKU and positive quantity") List<ReturnService.Selection> items,
            @ToolArg(description = "damaged, wrong_item, or changed_mind") String reason) {
        try {
            return returns.preview(orderId, new ReturnService.ReturnRequest(items, reason));
        } catch (ReturnService.InvalidReturn error) {
            throw new ToolCallException(error.getMessage());
        }
    }
}
