package vn.pharmacy.dto;

/** A foreign-key choice displayed by GUI; the stored value is always the id. */
public record LookupOption(String id, String label) {
  @Override
  public String toString() {
    return id + " — " + label;
  }
}
