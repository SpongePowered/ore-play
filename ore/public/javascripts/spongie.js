/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By Walker Crouse (windy) and contributors
 * (C) SpongePowered 2016-2017 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Powers the Spongie icon on the bottom of the page (requires svg.js).
 *
 * ==================================================
 */

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    var spongie = $('.col-spongie').find('div');
    spongie.click(function() { window.location = 'https://spongepowered.org' });
    spongie
        .mouseenter(function() { $('#spongie').find('path').css('fill', '#F7CF0D'); })
        .mouseleave(function() { $('#spongie').find('path').css('fill', 'gray'); });
});
