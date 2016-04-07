var BASE_URL = null;
var PROJECTS_PER_CLICK = 50;
var CATEGORY_STRING = null;

var currentlyLoaded = 0;

$(function() {
    $('.btn-more').click(function() {
        var ajaxUrl = BASE_URL + '/api/projects?limit=' + PROJECTS_PER_CLICK + '&offset=' + currentlyLoaded;
        if (CATEGORY_STRING) {
            ajaxUrl += '&categories=' + CATEGORY_STRING;
        }
        $('.btn-more').html('<i class="fa fa-spinner fa-spin"></i>');
        $.ajax({
            url: ajaxUrl,
            dataType: 'json',
            success: function(data) {
                for (var i in data) {
                    if (!data.hasOwnProperty(i)) {
                        continue;
                    }
                    var project = data[i];
                    var category = project.category;
                    var url = BASE_URL + '/' + project.owner + '/' + project.name;
                    $('.project-table').find('tbody').append(
                          '<tr>'
                          + '<td><i title="' + category.title + '" class="fa ' + category.icon + '"></i></td>'
                          + '<td colspan="1"><strong><a href="' + url + '">' + project.name + '</a></strong></td>'
                          + '<td><a href="#">' + project.owner + '</a></td>'
                          + '<td class="minor">' + project.views + '</td>'
                          + '<td class="minor">' + project.downloads + '</td>'
                          + '<td class="minor">' + project.stars + '</td>'
                        + '</tr>'
                    );
                }
                currentlyLoaded += data.length;
                $('.btn-more').html('<strong>More</strong>');
            }
        });
    });
});
