package models;

import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import play.db.jpa.Model;

@Entity
public class InventoryMovement extends Model {

  public Date creationDate;

  @ManyToOne
  public Item item;

  public Integer quantity;

  public InventoryMovement(Date creationDate, Item item, Integer quantity) {
    this.creationDate = creationDate;
    this.item = item;
    this.quantity = quantity;
  }
}
