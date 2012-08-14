package directoryServer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.xerces.util.XMLStringBuffer;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.xml.sax.ContentHandler;

class ServiceConsole implements Runnable {
    private static final String PROMPT = ">>> ";
    private final Map<String, ServiceCommand> commands = new TreeMap<String, ServiceCommand>();
    private final ExitNodeDB db;

    public ServiceConsole(ExitNodeDB dataBase) {
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
                    System.out.println(help());
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
            		OutputFormat of = new OutputFormat();
            		StringWriter sw = new StringWriter();
            		XMLSerializer serializer = new XMLSerializer(sw, of);
            		ContentHandler hd = serializer.asContentHandler();
            		hd.startDocument();
            		db.getUpdatesSince(0l, hd);
            		hd.endDocument();
            		System.out.println(sw.toString());
            	} catch(Exception e) {
            		e.printStackTrace();
            	}
            }

            @Override
            String help() {
                return "Usage: print\n\tPrints the contents of the ExitNode Database.";
            }
        });

        // IMPORT Command
        commands.put("import", new ServiceCommand() {

            @Override
            void perform(String[] args) {
                if (args.length == 2) {
                    try {
                        FileInputStream file = new FileInputStream(args[1]);
                        List<ExitNodeRecord> newNodes = new Parser(file).parseAsExitNodeList();
                        for (ExitNodeRecord node : newNodes) {
                            db.add(node);
                        }
                        db.saveEdits();
                    } catch (FileNotFoundException e) {
                        System.out.println("Invalid file specified.");
                    } catch (ParserConfigurationException e) {
                        e.printStackTrace();
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
