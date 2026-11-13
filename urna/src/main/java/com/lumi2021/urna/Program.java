package com.lumi2021.urna;

import com.lumi2021.urna.application.UrnaContext;
import com.lumi2021.urna.platform.boot.DesktopBootstrap;
import com.lumi2021.urna.platform.boot.EmbeddedBootstrap;
import com.lumi2021.urna.platform.boot.UrnaBootstrap;

public class Program {

    public static void main(String[] args) {
        boolean embedded = args.length >= 1 && (args[0].equals("--embedded") || args[0].equals("-e"));

        UrnaBootstrap mode = embedded
                ? new EmbeddedBootstrap()
                : new DesktopBootstrap();

        UrnaContext context = null;
        try {
            context = UrnaContext.conectar();
        }
        catch (Exception e) {
            System.err.println("Nao foi possivel carregar os dados do Firebase: " + e.getMessage());
            System.err.println("Verifique as variáveis de ambiente GOOGLE_APPLICATION_CREDENTIALS, " +
                    "FIREBASE_PROJECT_ID e ELEICAO_ID.");
            System.exit(1);
        }

        try {
            context.iniciarEleicao();
        }
        catch (Exception e) {
            System.err.println("Nao foi possivel iniciar a urna: " + e.getMessage());
            context.close();
            System.exit(1);
        }

        mode.start(context);
    }
}
