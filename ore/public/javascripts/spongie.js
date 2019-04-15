//=====> DOCUMENT READY

$(function() {
    var spongie = $('.col-spongie').find('div');
    spongie.click(function() { window.location = 'https://spongepowered.org' });
    spongie
        .mouseenter(function() { $('#spongie').find('path').css('fill', '#F7CF0D'); })
        .mouseleave(function() { $('#spongie').find('path').css('fill', 'gray'); });
});
