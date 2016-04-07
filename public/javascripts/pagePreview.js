var markdown = new showdown.Converter();

$(function() {
    $('[data-toggle="tab"]').click(function(event) {
        event.preventDefault();
        $(this).tab('show');
    });

    $('[href="#preview"]').on('shown.bs.tab', function() {
        var preview = $('#preview');
        preview.html(markdown.makeHtml($('#content').val()));
        preview.find('pre code').each(function(i, block) {
            hljs.highlightBlock(block);
        });
    });
});
