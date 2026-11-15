import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Vehicle Rental Management System - Java Swing (single file, no external libraries).
 * Login: admin / admin123
 * Data is saved automatically to "rental_data.txt" in the folder where you run the program.
 */
public class VehicleRentalSystem {

    // ===================== MODEL CLASSES =====================

    static class Vehicle {
        int id;
        String type, brand, model, plate;
        double ratePerDay;
        boolean available = true;

        Vehicle(int id, String type, String brand, String model, String plate, double rate) {
            this.id = id; this.type = type; this.brand = brand;
            this.model = model; this.plate = plate; this.ratePerDay = rate;
        }

        @Override public String toString() {
            return id + " - " + brand + " " + model + " (" + plate + ") Rs." + ratePerDay + "/day";
        }
    }

    static class Customer {
        int id;
        String name, phone, license;

        Customer(int id, String name, String phone, String license) {
            this.id = id; this.name = name; this.phone = phone; this.license = license;
        }

        @Override public String toString() {
            return id + " - " + name + " (" + phone + ")";
        }
    }

    static class Rental {
        int id, vehicleId, customerId, days;
        LocalDate startDate;
        boolean returned = false;
        LocalDate returnDate = null;
        double totalCost = 0;

        Rental(int id, int vehicleId, int customerId, LocalDate startDate, int days) {
            this.id = id; this.vehicleId = vehicleId; this.customerId = customerId;
            this.startDate = startDate; this.days = days;
        }
    }

    // ===================== DATA STORE (in-memory + file persistence) =====================

    static class DataStore {
        static final String FILE = "rental_data.txt";
        final List<Vehicle> vehicles = new ArrayList<>();
        final List<Customer> customers = new ArrayList<>();
        final List<Rental> rentals = new ArrayList<>();
        int nextVehicleId = 1, nextCustomerId = 1, nextRentalId = 1;

        Vehicle findVehicle(int id) {
            for (Vehicle v : vehicles) if (v.id == id) return v;
            return null;
        }

        Customer findCustomer(int id) {
            for (Customer c : customers) if (c.id == id) return c;
            return null;
        }

        boolean customerHasActiveRental(int customerId) {
            for (Rental r : rentals) if (r.customerId == customerId && !r.returned) return true;
            return false;
        }

        boolean plateExists(String plate) {
            for (Vehicle v : vehicles) if (v.plate.equalsIgnoreCase(plate)) return true;
            return false;
        }

        // Rent: cost = days * rate. Late return: extra days are charged at 1.5x the daily rate.
        double calculateCost(Rental r, Vehicle v, LocalDate returnDate) {
            long used = ChronoUnit.DAYS.between(r.startDate, returnDate);
            if (used < 1) used = 1;                       // minimum one day
            double cost = Math.min(used, r.days) * v.ratePerDay;
            if (used > r.days) cost += (used - r.days) * v.ratePerDay * 1.5;
            return cost;
        }

        double totalRevenue() {
            double sum = 0;
            for (Rental r : rentals) if (r.returned) sum += r.totalCost;
            return sum;
        }

        static String clean(String s) {            // '|' is our separator, so remove it from user text
            return s.trim().replace("|", " ");
        }

        void save() {
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(FILE), StandardCharsets.UTF_8))) {
                for (Vehicle v : vehicles)
                    pw.println("V|" + v.id + "|" + v.type + "|" + v.brand + "|" + v.model + "|"
                            + v.plate + "|" + v.ratePerDay + "|" + v.available);
                for (Customer c : customers)
                    pw.println("C|" + c.id + "|" + c.name + "|" + c.phone + "|" + c.license);
                for (Rental r : rentals)
                    pw.println("R|" + r.id + "|" + r.vehicleId + "|" + r.customerId + "|" + r.startDate
                            + "|" + r.days + "|" + r.returned + "|" + (r.returnDate == null ? "-" : r.returnDate)
                            + "|" + r.totalCost);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Could not save data: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        void load() {
            File f = new File(FILE);
            if (!f.exists()) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        String[] p = line.split("\\|");
                        switch (p[0]) {
                            case "V": {
                                Vehicle v = new Vehicle(Integer.parseInt(p[1]), p[2], p[3], p[4], p[5],
                                        Double.parseDouble(p[6]));
                                v.available = Boolean.parseBoolean(p[7]);
                                vehicles.add(v);
                                nextVehicleId = Math.max(nextVehicleId, v.id + 1);
                                break;
                            }
                            case "C": {
                                Customer c = new Customer(Integer.parseInt(p[1]), p[2], p[3], p[4]);
                                customers.add(c);
                                nextCustomerId = Math.max(nextCustomerId, c.id + 1);
                                break;
                            }
                            case "R": {
                                Rental r = new Rental(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                                        Integer.parseInt(p[3]), LocalDate.parse(p[4]), Integer.parseInt(p[5]));
                                r.returned = Boolean.parseBoolean(p[6]);
                                r.returnDate = p[7].equals("-") ? null : LocalDate.parse(p[7]);
                                r.totalCost = Double.parseDouble(p[8]);
                                rentals.add(r);
                                nextRentalId = Math.max(nextRentalId, r.id + 1);
                                break;
                            }
                            default: break;
                        }
                    } catch (Exception badLine) {
                        // skip a corrupted line instead of crashing the whole program
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Could not load data: " + e.getMessage(),
                        "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ===================== MAIN WINDOW =====================

    static class MainFrame extends JFrame {
        final DataStore db = new DataStore();

        final DefaultTableModel vehicleModel = newModel("ID", "Type", "Brand", "Model", "Plate", "Rate/Day", "Status");
        final DefaultTableModel customerModel = newModel("ID", "Name", "Phone", "License No");
        final DefaultTableModel rentalModel = newModel("ID", "Vehicle", "Customer", "Start Date",
                "Days", "Status", "Returned On", "Cost (Rs.)");

        final JTable vehicleTable = new JTable(vehicleModel);
        final JTable customerTable = new JTable(customerModel);
        final JTable rentalTable = new JTable(rentalModel);

        final JComboBox<Vehicle> vehicleCombo = new JComboBox<>();
        final JComboBox<Customer> customerCombo = new JComboBox<>();
        final JLabel statsLabel = new JLabel();

        MainFrame() {
            super("Vehicle Rental Management System");
            db.load();
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(950, 600);
            setLocationRelativeTo(null);

            statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD, 14f));
            statsLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Vehicles", buildVehiclePanel());
            tabs.addTab("Customers", buildCustomerPanel());
            tabs.addTab("Rentals", buildRentalPanel());

            add(statsLabel, BorderLayout.NORTH);
            add(tabs, BorderLayout.CENTER);
            refreshAll();
        }

        static DefaultTableModel newModel(String... cols) {
            return new DefaultTableModel(cols, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
        }

        // ---------- helper to build a labelled form ----------
        static void addRow(JPanel p, String label, JComponent field, int row) {
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 6, 4, 6);
            g.gridy = row; g.anchor = GridBagConstraints.WEST;
            g.gridx = 0; p.add(new JLabel(label), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            p.add(field, g);
        }

        void error(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }

        void info(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Information", JOptionPane.INFORMATION_MESSAGE);
        }

        // ---------- VEHICLES TAB ----------
        JPanel buildVehiclePanel() {
            JComboBox<String> typeBox = new JComboBox<>(new String[]{"Car", "SUV", "Bike", "Scooter", "Van"});
            JTextField brand = new JTextField(12), model = new JTextField(12),
                    plate = new JTextField(12), rate = new JTextField(12);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createTitledBorder("Add Vehicle"));
            addRow(form, "Type:", typeBox, 0);
            addRow(form, "Brand:", brand, 1);
            addRow(form, "Model:", model, 2);
            addRow(form, "Plate No:", plate, 3);
            addRow(form, "Rate per day (Rs.):", rate, 4);

            JButton add = new JButton("Add Vehicle");
            JButton delete = new JButton("Delete Selected");
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
            btns.add(add); btns.add(delete);
            JPanel left = new JPanel(new BorderLayout());
            left.add(form, BorderLayout.NORTH);
            left.add(btns, BorderLayout.CENTER);

            add.addActionListener(e -> {
                String b = DataStore.clean(brand.getText()), m = DataStore.clean(model.getText()),
                        pl = DataStore.clean(plate.getText()).toUpperCase();
                if (b.isEmpty() || m.isEmpty() || pl.isEmpty() || rate.getText().trim().isEmpty()) {
                    error("Please fill in all fields."); return;
                }
                double r;
                try {
                    r = Double.parseDouble(rate.getText().trim());
                } catch (NumberFormatException ex) {
                    error("Rate must be a number."); return;
                }
                if (Double.isNaN(r) || Double.isInfinite(r) || r <= 0) {
                    error("Rate must be greater than 0."); return;
                }
                if (db.plateExists(pl)) { error("A vehicle with this plate number already exists."); return; }
                db.vehicles.add(new Vehicle(db.nextVehicleId++, (String) typeBox.getSelectedItem(), b, m, pl, r));
                db.save();
                brand.setText(""); model.setText(""); plate.setText(""); rate.setText("");
                refreshAll();
            });

            delete.addActionListener(e -> {
                int row = vehicleTable.getSelectedRow();
                if (row < 0) { error("Select a vehicle first."); return; }
                Vehicle v = db.vehicles.get(row);
                if (!v.available) { error("This vehicle is currently rented and cannot be deleted."); return; }
                if (JOptionPane.showConfirmDialog(this, "Delete " + v.brand + " " + v.model + "?",
                        "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    db.vehicles.remove(v);
                    db.save();
                    refreshAll();
                }
            });

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel.add(left, BorderLayout.WEST);
            panel.add(new JScrollPane(vehicleTable), BorderLayout.CENTER);
            return panel;
        }

        // ---------- CUSTOMERS TAB ----------
        JPanel buildCustomerPanel() {
            JTextField name = new JTextField(12), phone = new JTextField(12), license = new JTextField(12);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createTitledBorder("Add Customer"));
            addRow(form, "Name:", name, 0);
            addRow(form, "Phone (10 digits):", phone, 1);
            addRow(form, "License No:", license, 2);

            JButton add = new JButton("Add Customer");
            JButton delete = new JButton("Delete Selected");
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
            btns.add(add); btns.add(delete);
            JPanel left = new JPanel(new BorderLayout());
            left.add(form, BorderLayout.NORTH);
            left.add(btns, BorderLayout.CENTER);

            add.addActionListener(e -> {
                String n = DataStore.clean(name.getText()), ph = phone.getText().trim(),
                        lic = DataStore.clean(license.getText()).toUpperCase();
                if (n.isEmpty() || ph.isEmpty() || lic.isEmpty()) { error("Please fill in all fields."); return; }
                if (!ph.matches("\\d{10}")) { error("Phone number must be exactly 10 digits."); return; }
                db.customers.add(new Customer(db.nextCustomerId++, n, ph, lic));
                db.save();
                name.setText(""); phone.setText(""); license.setText("");
                refreshAll();
            });

            delete.addActionListener(e -> {
                int row = customerTable.getSelectedRow();
                if (row < 0) { error("Select a customer first."); return; }
                Customer c = db.customers.get(row);
                if (db.customerHasActiveRental(c.id)) {
                    error("This customer has an active rental and cannot be deleted."); return;
                }
                if (JOptionPane.showConfirmDialog(this, "Delete customer " + c.name + "?",
                        "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    db.customers.remove(c);
                    db.save();
                    refreshAll();
                }
            });

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel.add(left, BorderLayout.WEST);
            panel.add(new JScrollPane(customerTable), BorderLayout.CENTER);
            return panel;
        }

        // ---------- RENTALS TAB ----------
        JPanel buildRentalPanel() {
            JSpinner days = new JSpinner(new SpinnerNumberModel(1, 1, 365, 1));

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createTitledBorder("New Rental"));
            addRow(form, "Vehicle:", vehicleCombo, 0);
            addRow(form, "Customer:", customerCombo, 1);
            addRow(form, "Number of days:", days, 2);

            JButton rent = new JButton("Rent Vehicle");
            JButton ret = new JButton("Return Selected Vehicle");
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
            btns.add(rent); btns.add(ret);
            JPanel top = new JPanel(new BorderLayout());
            top.add(form, BorderLayout.CENTER);
            top.add(btns, BorderLayout.SOUTH);

            rent.addActionListener(e -> {
                Vehicle v = (Vehicle) vehicleCombo.getSelectedItem();
                Customer c = (Customer) customerCombo.getSelectedItem();
                if (v == null) { error("No available vehicle to rent. Add a vehicle first."); return; }
                if (c == null) { error("No customer found. Add a customer first."); return; }
                int d = (Integer) days.getValue();
                db.rentals.add(new Rental(db.nextRentalId++, v.id, c.id, LocalDate.now(), d));
                v.available = false;
                db.save();
                refreshAll();
                info("Rental created.\nVehicle: " + v.brand + " " + v.model + "\nCustomer: " + c.name
                        + "\nExpected cost: Rs." + (d * v.ratePerDay));
            });

            ret.addActionListener(e -> {
                int row = rentalTable.getSelectedRow();
                if (row < 0) { error("Select a rental from the table first."); return; }
                Rental r = db.rentals.get(row);
                if (r.returned) { error("This vehicle has already been returned."); return; }
                Vehicle v = db.findVehicle(r.vehicleId);
                if (v == null) { error("Vehicle record not found."); return; }
                LocalDate today = LocalDate.now();
                r.totalCost = db.calculateCost(r, v, today);
                r.returned = true;
                r.returnDate = today;
                v.available = true;
                db.save();
                refreshAll();
                info("Vehicle returned.\nTotal bill: Rs." + r.totalCost);
            });

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(rentalTable), BorderLayout.CENTER);
            return panel;
        }

        // ---------- REFRESH EVERYTHING FROM THE DATA STORE ----------
        void refreshAll() {
            vehicleModel.setRowCount(0);
            int availableCount = 0;
            for (Vehicle v : db.vehicles) {
                if (v.available) availableCount++;
                vehicleModel.addRow(new Object[]{v.id, v.type, v.brand, v.model, v.plate,
                        v.ratePerDay, v.available ? "Available" : "Rented"});
            }

            customerModel.setRowCount(0);
            for (Customer c : db.customers)
                customerModel.addRow(new Object[]{c.id, c.name, c.phone, c.license});

            rentalModel.setRowCount(0);
            int active = 0;
            for (Rental r : db.rentals) {
                Vehicle v = db.findVehicle(r.vehicleId);
                Customer c = db.findCustomer(r.customerId);
                if (!r.returned) active++;
                rentalModel.addRow(new Object[]{
                        r.id,
                        v == null ? "(deleted)" : v.brand + " " + v.model,
                        c == null ? "(deleted)" : c.name,
                        r.startDate, r.days,
                        r.returned ? "Returned" : "Active",
                        r.returnDate == null ? "-" : r.returnDate,
                        r.returned ? r.totalCost : "-"});
            }

            vehicleCombo.removeAllItems();
            for (Vehicle v : db.vehicles) if (v.available) vehicleCombo.addItem(v);
            customerCombo.removeAllItems();
            for (Customer c : db.customers) customerCombo.addItem(c);

            statsLabel.setText("Vehicles: " + db.vehicles.size() + "   |   Available: " + availableCount
                    + "   |   Active Rentals: " + active + "   |   Revenue: Rs." + db.totalRevenue());
        }
    }

    // ===================== LOGIN =====================

    static boolean login() {
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        Object[] fields = {"Username:", user, "Password:", pass};
        for (int attempt = 1; attempt <= 3; attempt++) {
            int choice = JOptionPane.showConfirmDialog(null, fields,
                    "Admin Login (attempt " + attempt + " of 3)",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return false;
            if (user.getText().trim().equals("admin") && new String(pass.getPassword()).equals("admin123"))
                return true;
            JOptionPane.showMessageDialog(null, "Wrong username or password.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
            pass.setText("");
        }
        return false;
    }

    // ===================== ENTRY POINT =====================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            if (login()) new MainFrame().setVisible(true);
            else System.exit(0);
        });
    }
}
