package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;

public interface Command {
    CommandResult execute(DatabaseEngine engine);
}
