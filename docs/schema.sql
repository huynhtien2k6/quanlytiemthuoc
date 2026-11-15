-- SQLite reference model only. Java app uses TXT via DAO.
PRAGMA foreign_keys = ON;
CREATE TABLE employees (
 employee_id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL CHECK(length(trim(name))>0), address TEXT NOT NULL,
 gender TEXT NOT NULL CHECK(gender IN ('Nam','Nữ','Khác')), birth_date TEXT NOT NULL,
 phone TEXT NOT NULL, hired_date TEXT NOT NULL, email TEXT NOT NULL,
 salary NUMERIC NOT NULL CHECK(salary >= 0)
);
CREATE TABLE customers (
 customer_id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL CHECK(length(trim(name))>0), address TEXT NOT NULL,
 gender TEXT NOT NULL CHECK(gender IN ('Nam','Nữ','Khác')), birth_date TEXT NOT NULL, phone TEXT NOT NULL
);
CREATE TABLE suppliers (
 supplier_id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL CHECK(length(trim(name))>0), address TEXT NOT NULL, phone TEXT NOT NULL
);
CREATE TABLE medicines (
 medicine_id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL CHECK(length(trim(name))>0), unit TEXT NOT NULL CHECK(length(trim(unit))>0),
 selling_price NUMERIC NOT NULL CHECK(selling_price >= 0), prescription_required INTEGER NOT NULL CHECK(prescription_required IN (0,1)),
 ingredient_or_use TEXT NOT NULL, instructions TEXT NOT NULL
);
CREATE TABLE lots (
 lot_id TEXT PRIMARY KEY NOT NULL,
 medicine_id TEXT NOT NULL REFERENCES medicines(medicine_id) ON DELETE RESTRICT,
 expiry_date TEXT NOT NULL, opening_stock INTEGER NOT NULL CHECK(opening_stock >= 0)
);
CREATE TABLE purchases (
 purchase_id TEXT PRIMARY KEY NOT NULL, purchase_date TEXT NOT NULL,
 supplier_id TEXT NOT NULL REFERENCES suppliers(supplier_id) ON DELETE RESTRICT,
 employee_id TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE RESTRICT
);
CREATE TABLE purchase_lines (
 line_id TEXT PRIMARY KEY NOT NULL, purchase_id TEXT NOT NULL REFERENCES purchases(purchase_id) ON DELETE RESTRICT,
 lot_id TEXT NOT NULL REFERENCES lots(lot_id) ON DELETE RESTRICT,
 quantity INTEGER NOT NULL CHECK(quantity > 0), unit_price NUMERIC NOT NULL CHECK(unit_price >= 0),
 UNIQUE(purchase_id, lot_id)
);
CREATE TABLE sales (
 sale_id TEXT PRIMARY KEY NOT NULL, sale_date TEXT NOT NULL,
 customer_id TEXT NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
 employee_id TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE RESTRICT,
 prescription_ref TEXT NOT NULL DEFAULT ''
);
CREATE TABLE sale_lines (
 line_id TEXT PRIMARY KEY NOT NULL, sale_id TEXT NOT NULL REFERENCES sales(sale_id) ON DELETE RESTRICT,
 lot_id TEXT NOT NULL REFERENCES lots(lot_id) ON DELETE RESTRICT,
 quantity INTEGER NOT NULL CHECK(quantity > 0), unit_price NUMERIC NOT NULL CHECK(unit_price >= 0),
 UNIQUE(sale_id, lot_id)
);
-- Aggregate stock, expiry, prescription checks: implemented in PharmacyService.validate().
