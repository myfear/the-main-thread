package com.themainthread.shapeshift;

import dev.langchain4j.model.output.structured.Description;

public enum Intent {
    @Description("Scheduling a meeting, meal, call or gathering, usually with another person")
    EVENT,
    @Description("Asking to be reminded to do one task, such as paying rent or calling someone")
    REMINDER,
    @Description("Listing several separate tasks or items to buy")
    CHECKLIST,
    @Description("Starting a timer or focus session for a duration, not scheduling a calendar event")
    TIMER,
    @Description("Dividing a monetary amount between people, including a shared bill")
    SPLIT,
    @Description("A thought, idea, observation or other complete input that does not fit the other cards")
    NOTE,
    @Description("An unfinished fragment with too little information to choose a card yet")
    NONE
}
