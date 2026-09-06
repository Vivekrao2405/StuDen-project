package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One module inside a {@link PlacementSeries} (Programming Fundamentals, SQL, Aptitude, ...).
 *
 * <p>{@code displayOrder} carries the spec requirement to reorder modules and is intentionally not
 * unique per series, because reordering swaps values in place and a unique constraint would reject
 * the intermediate state — the same reason {@code Topic.displayOrder} has none.
 *
 * <p>{@code requiredItemCount} is the completion requirement: null means every required item must
 * be completed, a number means at least that many. Phase 0 stores it; the progress engine that
 * evaluates it belongs to a later phase.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_modules")
public class PlacementModule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private PlacementSeries series;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "required_item_count")
    private Integer requiredItemCount;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<PlacementModuleItem> items = new ArrayList<>();

    public PlacementModule(PlacementSeries series, String name, int displayOrder) {
        this.series = series;
        this.name = name;
        this.displayOrder = displayOrder;
    }
}
