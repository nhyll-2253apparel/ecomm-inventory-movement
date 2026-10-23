package com._apparel.ecomm_inventory_movement.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INVENTORY_LI", schema = "NKW")
@Data
@IdClass(InventoryLiId.class)
public class InventoryLi {

    @Id
    @Column(name = "WHOUSE")
    private String whouse;

    @Id
    @Column(name = "STYLE")
    private String style;

    @Id
    @Column(name = "COLOR_NO")
    private String colorNo;

    @Column(name = "ON_HAND")
    private Long onHand;

    @Column(name = "OPEN_ORDERS")
    private Long openOrders;

    @Column(name = "PICKTKT")
    private Long picktkt;

    @Column(name = "ATS_STOCK")
    private Long atsStock;

    @Column(name = "WIP")
    private Long wip;

    @Column(name = "ATS")
    private Long ats;
}
