var baseUrl = null;
var projectOwner = null;
var projectSlug = null;
var alreadyStarred = false;

$(function() {

    // setup star button
    var increment = alreadyStarred ? -1 : 1;
    $('.btn-star').click(function() {
        var starred = $(this).find('.starred');
        starred.html(' ' + (parseInt(starred.text()) + increment).toString());
        $.ajax(baseUrl + '/' + projectOwner + '/' + projectSlug + '/star/' + (increment > 0));

        var icon = $('#icon-star');
        if (increment > 0) {
            icon.removeClass('fa-star-o').addClass('fa-star');
        } else {
            icon.removeClass('fa-star').addClass('fa-star-o');
        }

        increment *= -1;
    });

});
