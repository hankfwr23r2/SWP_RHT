ALTER TABLE cost_item ADD COLUMN stabling_delivery_credit numeric(15,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_cost_item_stabling_delivery_credit CHECK (
        stabling_delivery_credit >= 0 AND stabling_deposit_credit + stabling_delivery_credit <= amount
        AND (stabling_delivery_credit = 0 OR (category = 'STABLING' AND horse_stay_id IS NOT NULL))
    );
