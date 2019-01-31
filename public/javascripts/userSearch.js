function initUserSearch(callback) {
    var search = $('.list-members .user-search');
    var input = search.find('input');

    // Disable button with no input
    input.on('input', function() {
        $(this).next().find('.btn').prop('disabled', $(this).val().length === 0);
    });

    // Catch enter key
    input.on('keypress', function(event) {
        if (event.keyCode === KEY_ENTER) {
            event.preventDefault();
            $(this).next().find('.btn').click();
        }
    });

    // Search for user
    search.find('.btn-search').click(function() {
        var input = $(this).closest('.user-search').find('input');
        var username = input.val().trim();
        var icon = toggleSpinner(search.find('[data-fa-i2svg]').toggleClass('fa-search'));

        $.ajax(jsRoutes.controllers.ApiController.showUser(API_VERSION, username))
            .always(function () {
                input.val('');
                toggleSpinner(icon.refresh().toggleClass('fa-search').prop('disabled', true))
            })
            .done(function (user) {
                callback({
                    isSuccess: true,
                    username: username,
                    user: user
                });
            })
            .fail(function () {
                callback({
                    isSuccess: false,
                    username: username,
                    user: null
                })
            });
    });
}
