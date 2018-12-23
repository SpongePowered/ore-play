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
 * Powers the user page.
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var USERNAME = null;
var STARS_PER_PAGE = 5;
var currentStarsPage = 1;

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function getStarsPanel() {
    return $('.panel-stars');
}

function getStarsFooter() {
    return getStarsPanel().find('.panel-footer');
}

function loadStars(increment) {
    // Request user data
    $.ajax({
        url: 'api/users/' + USERNAME,
        dataType: 'json',
        success: function(userData) {
            var allStars = userData.starred;
            var start = (currentStarsPage - 1) * STARS_PER_PAGE + STARS_PER_PAGE * increment;
            var end = Math.min(start + STARS_PER_PAGE, allStars.length + 1);
            var newStars = allStars.slice(start, end);
            var tbody = getStarsPanel().find('.panel-body').find('tbody');
            var content = '';
            var count = 0;

            for (var i in newStars) {
                if (!newStars.hasOwnProperty(i)) {
                    continue;
                }
                var star = newStars[i];

                // Request project data for this Star
                $.ajax({
                    url: 'api/projects/' + star,
                    dataType: 'json',
                    error: function() {
                        ++count;
                    },
                    success: function(projectData) {
                        var href = projectData.href;
                        var slug = href.substr(href.lastIndexOf('/') + 1, href.length);
                        var category = projectData.category;

                        // Append project contents to result
                        content +=
                            '<tr>'
                            + '<td>'
                            +   '<a href="' + href + '">' + projectData.owner + '/<strong>' + slug + '</strong></a>'
                            +   '<div class="pull-right">'
                            +     '<span class="minor">' + projectData.recommended.name + '</span> '
                            +     '<i title="' + category.title + '" class="fas fa-fw ' + category.icon + '"></i>'
                            +   '</div>'
                            + '</td>';

                        ++count
                    },
                    complete: function()  {
                        if (count == newStars.length) {
                            // Done loading, set the table to the result
                            tbody.html(content);
                            currentStarsPage += increment;
                            var footer = getStarsFooter();
                            var prev = footer.find('.prev');

                            // Check if there is a last page
                            if (currentStarsPage > 1) {
                                prev.show();
                            } else {
                                prev.hide();
                            }

                            // Check if there is a next page
                            var next = footer.find('.next');
                            if (end < allStars.length) {
                                next.show();
                            } else {
                                next.hide();
                            }
                        }
                    }
                });
            }
        }
    });
}

function formAsync(form, route, onSuccess) {
    form.submit(function(e) {
        e.preventDefault();
        var formData = new FormData(this);
        var spinner = $(this).find('.fa-spinner').show();
        $.ajax({
            url: route,
            data: formData,
            cache: false,
            contentType: false,
            processData: false,
            type: 'post',
            dataType: 'json',
            complete: function() {
                spinner.hide();
            },
            success: onSuccess
        });
    });
}

function setupAvatarForm() {

    $('.btn-got-it').click(function() {
        var prompt = $(this).closest('.prompt');
        $.ajax({
            type: 'post',
            url: 'prompts/read/' + prompt.data('prompt-id'),
            data: { csrfToken: csrf }
        });
        prompt.fadeOut('fast');
    });

    $('.organization-avatar').hover(function() {
        $('.edit-avatar').fadeIn('fast');
    }, function(e) {
        if(!$(e.relatedTarget).closest("div").hasClass("edit-avatar")) {
            $('.edit-avatar').fadeOut('fast');
        }
    });

    var avatarModal = $('#modal-avatar');
    avatarModal.find('.alert').hide();

    var avatarForm = avatarModal.find('#form-avatar');
    avatarForm.find('input[name="avatar-method"]').change(function() {
        avatarForm.find('input[name="avatar-file"]').prop('disabled', $(this).val() !== 'by-file');
    });

    formAsync(avatarForm, 'organizations/' + USERNAME + '/settings/avatar', function(json) {
        if (json.hasOwnProperty('errors')) {
            var alert = avatarForm.find('.alert-danger');
            alert.find('.error').text(json['errors'][0]);
            alert.fadeIn('slow');
        } else {
            avatarModal.modal('hide');
            var success = $('.alert-success');
            success.find('.success').text('Avatar successfully updated!');
            success.fadeIn('slow');
            $('.user-avatar[title="' + USERNAME + '"]')
                .prop('src', json['avatarTemplate'].replace('{size}', '200'));
        }
    });
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    var footer = getStarsFooter();
    footer.find('.next').click(function() { loadStars(1); });
    footer.find('.prev').click(function() { loadStars(-1); });
    setupAvatarForm();
});
