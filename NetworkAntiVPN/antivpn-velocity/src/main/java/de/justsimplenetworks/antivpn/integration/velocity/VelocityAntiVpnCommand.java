package de.justsimplenetworks.antivpn.integration.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import de.justsimplenetworks.antivpn.commands.AntiVpnCommandExecutor;

public final class VelocityAntiVpnCommand implements SimpleCommand {

    private final AntiVpnCommandExecutor executor;

    public VelocityAntiVpnCommand(AntiVpnCommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Invocation invocation) {
        executor.execute(new VelocityCommandSource(invocation.source()), invocation.arguments());
    }
}
