package directoryServer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

class ServiceConsole implements Runnable {
    private static final String PROMPT = ">>> ";
    private final Map<String, ServiceCommand> commands = new TreeMap<String, ServiceCommand>();
    private final DirectoryDB db;

    public ServiceConsole(DirectoryDB dataBase) {
        this.db = dataBase;

        // HELP Command
        commands.put("help", new ServiceCommand() {
            @Override
            void perform(String[] args) {
                if (args.length < 2) {
                    StringBuilder sb = new StringBuilder("Commands:");
                    for (String command : commands.keySet()) {
                        sb.append(" " + command);
                    }
                    System.out.println(sb.toString());
                } else if (commands.containsKey(args[1])) {
                    System.out.println(commands.get(args[1]).help());
                } else {
                    System.out.println("Unable to display help about that command.");
                }
            }

            @Override
            String help() {
                return "Usage: help <command>\n\tDisplays help about a command.";
            }
        });

        // PRINT Command
        commands.put("print", new ServiceCommand() {
            @Override
            void perform(String[] args) {
                try {
                    XMLHelper xmlOut = new XMLHelper(System.out);
                    db.getUpdatesSince(0l, xmlOut);
                    xmlOut.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            String help() {
                return "Usage: print\n\tPrints the contents of the ExitNode Database.";
            }
        });

        // CLEAR Command
        commands.put("clear", new ServiceCommand() {
            @Override
            void perform(String[] args) {
                try {
                    db.clear();
                    System.out.println("Cleared.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            String help() {
                return "Usage: clear\n\tClears the contents of the ExitNode Database.";
            }
        });

        // IMPORT Command
        commands.put("import", new ServiceCommand() {

            @Override
            void perform(String[] args) {
                if (args.length == 2) {
                    try {
                        FileInputStream file = new FileInputStream(args[1]);
                        XMLHelper xmlOut = new XMLHelper(System.out);
                        List<DirectoryRecord> newNodes = new LinkedList<DirectoryRecord>();
                        XMLHelper.validateDigest = false;
                        XMLHelper.parse(file, new DirectoryRecordHandler(newNodes, xmlOut), null);
                        xmlOut.close();
                        for (DirectoryRecord node : newNodes) {
                            db.add(node, new XMLHelper(System.out));
                        }
                        db.saveEdits();
                    } catch (FileNotFoundException e) {
                        System.out.println("Invalid file specified.");
                    } catch (Exception e) {
                        System.out.println(e.toString());
                    }
                } else {
                    System.out.println(help());
                }
            }

            @Override
            String help() {
                return "Usage: import <file>\n\tTrys to imports the given XML file into the database.";
            }
        });
    }

    @Override
    public void run() {
        Scanner in = new Scanner(System.in);
        String[] input;
        while (true) {
            System.out.print(PROMPT);
            input = in.nextLine().split(" ");

            if (input.length < 1 || input[0].equals("exit")) {
                break;
            } else if (commands.containsKey(input[0])) {
                commands.get(input[0]).perform(input);
            } else {
                System.out.println("Unrecognized command");
            }
        }
    }

    private abstract class ServiceCommand {
        /**
         * Does the command.
         * 
         * @param args
         *            The tokens sent to the command line.
         */
        abstract void perform(String[] args);

        /**
         * Returns a short help message indicating the command's syntax and what
         * it does.
         * 
         * @return
         */
        abstract String help();
    }
}
